package com.werewolfengine.ai.api;

import com.werewolfengine.ai.config.AiProperties;
import com.werewolfengine.ai.guard.AiLegalActions;
import com.werewolfengine.ai.parse.AiIntentParser;
import com.werewolfengine.ai.policy.MockAIPlayer;
import com.werewolfengine.ai.memory.MemoryPromptFormatter;
import com.werewolfengine.ai.prompt.AiPromptBuilder;
import com.werewolfengine.game.engine.GameStateMachine;
import com.werewolfengine.game.model.GameActionType;
import com.werewolfengine.game.model.GamePhase;
import com.werewolfengine.game.model.GameRoomState;
import com.werewolfengine.game.model.PlayerState;
import com.werewolfengine.game.model.Role;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AIServiceTest {

    private GameStateMachine stateMachine;
    private AiProperties properties;
    private ChatModel chatModel;
    private AIService aiService;

    @BeforeEach
    void setUp() {
        stateMachine = new GameStateMachine();
        properties = new AiProperties();
        properties.setEnabled(true);
        properties.setWolvesOnly(false);
        chatModel = mock(ChatModel.class);
        MockAIPlayer mock = new MockAIPlayer();
        aiService = new AIService(
                properties,
                mock,
                new AiPromptBuilder(),
                new MemoryPromptFormatter(),
                new AiIntentParser(),
                new AiLegalActions(),
                chatModel,
                null
        );
    }

    @Test
    void usesLlmKillWhenValid() {
        when(chatModel.chat(anyList())).thenReturn(ChatResponse.builder()
                .aiMessage(dev.langchain4j.data.message.AiMessage.from("""
                        {"thinking":"刀5","action":"KILL","target":5,"reason":"刀村民"}
                        """))
                .build());

        String roomId = "llm_kill";
        stateMachine.createRoom(roomId);
        stateMachine.markAllReady(roomId);
        stateMachine.startGame(roomId);
        GameRoomState room = stateMachine.getRoom(roomId).orElseThrow();

        int wolf = room.aliveWolfIds().getFirst();
        Optional<PlayerIntent> intent = aiService.decide(room, wolf);
        assertThat(intent).isPresent();
        assertThat(intent.get().action()).isEqualTo(GameActionType.KILL);
        assertThat(intent.get().target()).isEqualTo(5);
    }

    @Test
    void fallsBackWhenLlmReturnsIllegalAction() {
        when(chatModel.chat(anyList())).thenReturn(ChatResponse.builder()
                .aiMessage(dev.langchain4j.data.message.AiMessage.from("""
                        {"action":"POISON","target":99}
                        """))
                .build());

        String roomId = "llm_fallback";
        stateMachine.createRoom(roomId);
        stateMachine.markAllReady(roomId);
        stateMachine.startGame(roomId);
        GameRoomState room = stateMachine.getRoom(roomId).orElseThrow();
        int wolf = mockKillWolfSeat(room);

        Optional<PlayerIntent> intent = aiService.decide(room, wolf);
        assertThat(intent).isPresent();
        assertThat(intent.get().action()).isEqualTo(GameActionType.KILL);
    }

    @Test
    void wolvesOnlySkipsLlmForVillagerDiscuss() {
        properties.setWolvesOnly(true);
        when(chatModel.chat(anyList())).thenReturn(ChatResponse.builder()
                .aiMessage(dev.langchain4j.data.message.AiMessage.from("{\"action\":\"VOTE\",\"target\":1}"))
                .build());

        GameRoomState room = new GameRoomState("wolves_only");
        PlayerState villager = room.getPlayer(1);
        villager.setRole(Role.VILLAGER);
        villager.setReady(true);
        room.setPhase(GamePhase.DAY_VOTE);
        room.setRound(1);
        for (int i = 2; i <= 12; i++) {
            room.getPlayer(i).setRole(Role.VILLAGER);
        }

        Optional<PlayerIntent> intent = aiService.decide(room, 1);
        assertThat(intent).isPresent();
        assertThat(intent.get().action()).isEqualTo(GameActionType.VOTE);
    }

    @Test
    void usesReasoningContentWhenTextEmpty() {
        when(chatModel.chat(anyList())).thenReturn(ChatResponse.builder()
                .aiMessage(AiMessage.builder()
                        .text("")
                        .thinking("{\"action\":\"KILL\",\"target\":4,\"reason\":\"刀4\"}")
                        .build())
                .build());

        String roomId = "llm_thinking";
        stateMachine.createRoom(roomId);
        stateMachine.markAllReady(roomId);
        stateMachine.startGame(roomId);
        GameRoomState room = stateMachine.getRoom(roomId).orElseThrow();
        int wolf = room.aliveWolfIds().getFirst();

        Optional<PlayerIntent> intent = aiService.decide(room, wolf);
        assertThat(intent).isPresent();
        assertThat(intent.get().action()).isEqualTo(GameActionType.KILL);
        assertThat(intent.get().target()).isEqualTo(4);
    }

    @Test
    void disabledUsesMockOnly() {
        properties.setEnabled(false);
        AIService disabled = new AIService(
                properties,
                new MockAIPlayer(),
                new AiPromptBuilder(),
                new MemoryPromptFormatter(),
                new AiIntentParser(),
                new AiLegalActions(),
                chatModel,
                null
        );

        String roomId = "mock_only";
        stateMachine.createRoom(roomId);
        stateMachine.markAllReady(roomId);
        stateMachine.startGame(roomId);
        GameRoomState room = stateMachine.getRoom(roomId).orElseThrow();
        int wolf = mockKillWolfSeat(room);

        Optional<PlayerIntent> intent = disabled.decide(room, wolf);
        assertThat(intent).isPresent();
        assertThat(intent.get().action()).isEqualTo(GameActionType.KILL);
    }

    private static int mockKillWolfSeat(GameRoomState room) {
        List<Integer> wolves = room.aliveWolfIds().stream().sorted().toList();
        if (wolves.size() >= 2) {
            return wolves.get(1);
        }
        room.setWolfChatInPhase(true);
        return wolves.getFirst();
    }
}
