package com.werewolfengine.ai.api;

import com.werewolfengine.ai.agent.AiAgent;
import com.werewolfengine.ai.config.AiProperties;
import com.werewolfengine.ai.guard.AiLegalActions;
import com.werewolfengine.ai.memory.MemoryPromptFormatter;
import com.werewolfengine.ai.parse.AiIntentParser;
import com.werewolfengine.ai.perceive.GameViewContext;
import com.werewolfengine.ai.policy.MockAIPlayer;
import com.werewolfengine.ai.prompt.AiPromptBuilder;
import com.werewolfengine.ai.prompt.Persona;
import com.werewolfengine.game.observability.ActionLogEntry;
import com.werewolfengine.game.view.SeatPerceptionProjector;
import com.werewolfengine.game.view.SeatPerceptionSlice;
import com.werewolfengine.game.model.GameActionType;
import com.werewolfengine.game.model.GamePhase;
import com.werewolfengine.game.model.GameRoomState;
import com.werewolfengine.game.model.PlayerState;
import com.werewolfengine.game.model.Role;
import com.werewolfengine.game.observability.ActionLogService;
import com.werewolfengine.game.view.GameView;
import com.werewolfengine.game.view.GameViews;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LangChain4j + DeepSeek intent generation (PRD §4.5). Falls back to {@link MockAIPlayer} on
 * timeout, parse error, or illegal action (retry 0).
 */
@Service
public class AIService {

    private static final Logger log = LoggerFactory.getLogger(AIService.class);

    private final AiProperties properties;
    private final MockAIPlayer mockAIPlayer;
    private final AiPromptBuilder promptBuilder;
    private final MemoryPromptFormatter memoryFormatter;
    private final AiIntentParser intentParser;
    private final AiLegalActions legalActions;
    private final Optional<ChatModel> chatModel;
    private final Optional<ActionLogService> actionLog;
    private final ConcurrentHashMap<String, AiAgent> agents = new ConcurrentHashMap<>();

    public AIService(
            AiProperties properties,
            MockAIPlayer mockAIPlayer,
            AiPromptBuilder promptBuilder,
            MemoryPromptFormatter memoryFormatter,
            AiIntentParser intentParser,
            AiLegalActions legalActions,
            @Autowired(required = false) ChatModel chatModel,
            @Autowired(required = false) ActionLogService actionLog
    ) {
        this.properties = properties;
        this.mockAIPlayer = mockAIPlayer;
        this.promptBuilder = promptBuilder;
        this.memoryFormatter = memoryFormatter;
        this.intentParser = intentParser;
        this.legalActions = legalActions;
        this.chatModel = Optional.ofNullable(chatModel);
        this.actionLog = Optional.ofNullable(actionLog);
    }

    public Optional<PlayerIntent> decide(GameRoomState room, int playerId) {
        if (isHumanSeat(room, playerId)) {
            return Optional.empty();
        }
        Optional<PlayerIntent> fallback = mockAIPlayer.decide(room, playerId);
        if (fallback.isEmpty()) {
            return Optional.empty();
        }
        if (!shouldUseLlm(room, playerId)) {
            return fallback;
        }
        try {
            PlayerIntent fromLlm = requestLlmIntent(room, playerId);
            if (legalActions.isLegal(room, playerId, fromLlm)) {
                log.debug("LLM intent room={} seat={} action={}", room.getRoomId(), playerId, fromLlm.action());
                recordLlmThinking(room, playerId, fromLlm);
                return Optional.of(fromLlm);
            }
            log.warn("LLM illegal intent room={} seat={} action={}, using fallback",
                    room.getRoomId(), playerId, fromLlm.action());
        } catch (Exception e) {
            log.warn("LLM failed room={} seat={}: {}, using fallback",
                    room.getRoomId(), playerId, e.toString());
        }
        return fallback;
    }

    private AiAgent agentFor(GameRoomState room, int playerId) {
        String key = room.getRoomId() + ":" + playerId;
        return agents.computeIfAbsent(key, k -> new AiAgent(playerId));
    }

    private static boolean isHumanSeat(GameRoomState room, int playerId) {
        PlayerState p = room.getPlayer(playerId);
        return p != null && p.getHumanUserId() != null;
    }

    private boolean shouldUseLlm(GameRoomState room, int playerId) {
        if (isHumanSeat(room, playerId)) {
            return false;
        }
        if (!properties.isEnabled() || chatModel.isEmpty()) {
            return false;
        }
        if (!properties.isWolvesOnly()) {
            return true;
        }
        PlayerState p = room.getPlayer(playerId);
        Role role = p != null ? p.getRole() : null;
        return room.getPhase() == GamePhase.NIGHT_WOLF && role == Role.WEREWOLF;
    }

    private PlayerIntent requestLlmIntent(GameRoomState room, int playerId) {
        GameView gameView = GameViews.forSeat(room, playerId);
        GameViewContext view = GameViewContext.from(gameView);
        Set<GameActionType> allowed = legalActions.allowed(room, playerId);
        if (allowed.isEmpty()) {
            throw new IllegalStateException("no legal actions for seat " + playerId);
        }
        Persona persona = agentFor(room, playerId).persona();
        String system = promptBuilder.systemMessage(persona);
        String memoryBlock = buildMemoryBlock(room, playerId);
        String user = promptBuilder.userMessage(view, allowed, memoryBlock);
        ChatResponse response = chatModel.orElseThrow().chat(
                List.of(SystemMessage.from(system), UserMessage.from(user))
        );
        return intentParser.parse(llmPayload(response.aiMessage()));
    }

    private String buildMemoryBlock(GameRoomState room, int playerId) {
        if (!properties.getMemory().isEnabled() || actionLog.isEmpty()) {
            return null;
        }
        List<ActionLogEntry> log = actionLog.get().getLog(room.getRoomId());
        SeatPerceptionSlice slice = SeatPerceptionProjector.project(room, playerId, log);
        var opts = properties.getMemory();
        var formatOpts = new MemoryPromptFormatter.FormatOptions(
                opts.getMaxEvents(),
                opts.getMaxChars(),
                opts.isIncludeOwnThinking()
        );
        return memoryFormatter.format(slice, formatOpts, true).text();
    }

    private void recordLlmThinking(GameRoomState room, int playerId, PlayerIntent fromLlm) {
        if (fromLlm.thinking() == null || fromLlm.thinking().isBlank()) {
            return;
        }
        actionLog.ifPresent(log -> {
            var p = room.getPlayer(playerId);
            log.recordAiThinking(
                    room.getRoomId(),
                    playerId,
                    p != null ? p.getRole() : null,
                    room.getPhase(),
                    room.getRound(),
                    fromLlm.action(),
                    fromLlm.thinking(),
                    properties.getModelId()
            );
        });
    }

    static String llmPayload(AiMessage message) {
        String text = message.text();
        if (text != null && !text.isBlank()) {
            return text.trim();
        }
        String thinking = message.thinking();
        if (thinking != null && !thinking.isBlank()) {
            return thinking.trim();
        }
        throw new IllegalArgumentException("empty LLM response");
    }
}
