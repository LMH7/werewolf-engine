package com.werewolfengine.game.orchestration;

import com.werewolfengine.game.engine.GameStateMachine;
import com.werewolfengine.game.model.GamePhase;
import com.werewolfengine.game.model.GameRoomState;
import com.werewolfengine.game.observability.ActionLogService;
import org.springframework.stereotype.Component;

/**
 * Phase advance hook for gateway (B): timer calls {@link #tick(String)} instead of busy-wait.
 */
@Component
public class GamePhaseScheduler {

    private final GameStateMachine stateMachine;
    private final AiTurnCoordinator turnCoordinator;
    private final ActionLogService actionLog;

    public GamePhaseScheduler(
            GameStateMachine stateMachine,
            AiTurnCoordinator turnCoordinator,
            ActionLogService actionLog
    ) {
        this.stateMachine = stateMachine;
        this.turnCoordinator = turnCoordinator;
        this.actionLog = actionLog;
    }

    public TickResult tick(String roomId) {
        GameRoomState room = stateMachine.getRoom(roomId)
                .orElseThrow(() -> new IllegalArgumentException("Room not found: " + roomId));
        GamePhase phase = room.getPhase();
        if (phase == GamePhase.GAME_OVER) {
            return TickResult.gameOver(room.getWinner() != null ? room.getWinner().name() : "");
        }
        if (phase == GamePhase.NIGHT_DEATH_ANNOUNCE || phase == GamePhase.EXILE_DEATH_ANNOUNCE) {
            int round = room.getRound();
            stateMachine.advanceDayAnnounce(roomId);
            actionLog.recordSystemEvent(roomId, round, phase, "advanceDayAnnounce", null);
            return TickResult.advanced(phase.name());
        }
        if (isPlayerPhase(phase)) {
            boolean stepped = turnCoordinator.tickOneStep(roomId, room);
            return stepped ? TickResult.aiStep(phase.name()) : TickResult.stuck(phase.name());
        }
        return TickResult.noOp(phase.name());
    }

    private static boolean isPlayerPhase(GamePhase phase) {
        return switch (phase) {
            case NIGHT_WOLF, NIGHT_SEER, NIGHT_WITCH, DAY_DISCUSS, DAY_VOTE, HUNTER_SHOOT, LAST_WORDS ->
                    true;
            default -> false;
        };
    }

    public record TickResult(String status, String phase, String detail) {
        public static TickResult advanced(String fromPhase) {
            return new TickResult("ADVANCED", fromPhase, "advanceDayAnnounce");
        }

        public static TickResult aiStep(String phase) {
            return new TickResult("AI_STEP", phase, "handleAction");
        }

        public static TickResult stuck(String phase) {
            return new TickResult("STUCK", phase, "no actor intent");
        }

        public static TickResult noOp(String phase) {
            return new TickResult("NO_OP", phase, "wait for start or system phase");
        }

        public static TickResult gameOver(String winner) {
            return new TickResult("GAME_OVER", GamePhase.GAME_OVER.name(), winner);
        }
    }
}
