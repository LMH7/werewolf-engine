package com.werewolfengine.game.orchestration;

import com.werewolfengine.ai.api.AIService;
import com.werewolfengine.ai.api.PlayerIntent;
import com.werewolfengine.game.engine.GameStateMachine;
import com.werewolfengine.game.model.ActionAck;
import com.werewolfengine.game.model.GameActionCommand;
import com.werewolfengine.game.model.GameActionType;
import com.werewolfengine.game.model.GamePhase;
import com.werewolfengine.game.model.GameRoomState;
import com.werewolfengine.game.observability.ActionLogService;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Single-step AI turn orchestration for gateway ticks and dev runners (ADR-003 §4).
 */
@Component
public class AiTurnCoordinator {

    private final GameStateMachine stateMachine;
    private final TurnActorResolver actorResolver;
    private final AIService aiService;
    private final ActionLogService actionLog;

    public AiTurnCoordinator(
            GameStateMachine stateMachine,
            TurnActorResolver actorResolver,
            AIService aiService,
            ActionLogService actionLog
    ) {
        this.stateMachine = stateMachine;
        this.actorResolver = actorResolver;
        this.aiService = aiService;
        this.actionLog = actionLog;
    }

    /**
     * Advances one logical step: system announce, or one AI {@link GameStateMachine#handleAction}.
     *
     * @return true if state changed; false if stuck (no actor / no intent / human seat)
     */
    public boolean tickOneStep(String roomId, GameRoomState room) {
        return switch (room.getPhase()) {
            case NIGHT_DEATH_ANNOUNCE, EXILE_DEATH_ANNOUNCE -> {
                GamePhase before = room.getPhase();
                int round = room.getRound();
                stateMachine.advanceDayAnnounce(roomId);
                actionLog.recordSystemEvent(roomId, round, before, "advanceDayAnnounce", null);
                yield true;
            }
            default -> submitNextAiIntent(roomId, room);
        };
    }

    private boolean submitNextAiIntent(String roomId, GameRoomState room) {
        Optional<Integer> actorId = actorResolver.nextAiActor(room);
        if (actorId.isEmpty()) {
            return false;
        }
        int playerId = actorId.get();
        GameRoomState fresh = stateMachine.getRoom(roomId).orElse(room);
        Optional<PlayerIntent> intent = aiService.decide(fresh, playerId);
        if (intent.isEmpty()) {
            return false;
        }
        PlayerIntent in = intent.get();
        int round = fresh.getRound();
        GamePhase phase = fresh.getPhase();
        Integer effectiveTarget = effectiveLogTarget(fresh, in.action(), in.target());
        GameActionCommand cmd = new GameActionCommand(
                playerId,
                in.action(),
                in.target(),
                phase,
                in.content()
        );
        GameStateMachine.HandleActionResult result = stateMachine.handleAction(roomId, cmd);
        GameRoomState after = stateMachine.getRoom(roomId).orElse(fresh);
        ActionAck ack = result.ack();
        actionLog.recordPlayerAction(roomId, round, phase, after, cmd, ack, effectiveTarget);
        return true;
    }

    private static Integer effectiveLogTarget(GameRoomState room, GameActionType action, Integer commandTarget) {
        if (action == GameActionType.SAVE) {
            return room.getPendingWolfKillTarget();
        }
        return commandTarget;
    }
}
