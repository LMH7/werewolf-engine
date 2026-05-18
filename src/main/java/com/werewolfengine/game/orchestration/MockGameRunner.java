package com.werewolfengine.game.orchestration;

import com.werewolfengine.game.engine.GameStateMachine;
import com.werewolfengine.game.model.GamePhase;
import com.werewolfengine.game.model.GameRoomState;
import com.werewolfengine.game.model.GameWinner;
import org.springframework.stereotype.Component;

/**
 * Dev / load-test driver: runs {@link AiTurnCoordinator} until {@link GamePhase#GAME_OVER} (ADR-003).
 */
@Component
public class MockGameRunner {

    public static final int DEFAULT_MAX_STEPS = 8_000;

    private final GameStateMachine stateMachine;
    private final AiTurnCoordinator turnCoordinator;

    public MockGameRunner(GameStateMachine stateMachine, AiTurnCoordinator turnCoordinator) {
        this.stateMachine = stateMachine;
        this.turnCoordinator = turnCoordinator;
    }

    public RunResult runUntilGameOver(String roomId) {
        return runUntilGameOver(roomId, DEFAULT_MAX_STEPS);
    }

    public RunResult runUntilGameOver(String roomId, int maxSteps) {
        int steps = 0;
        GamePhase lastPhase = null;
        while (steps < maxSteps) {
            GameRoomState room = stateMachine.getRoom(roomId)
                    .orElseThrow(() -> new IllegalArgumentException("Room not found: " + roomId));
            if (room.getPhase() == GamePhase.GAME_OVER) {
                return RunResult.finished(steps, room.getWinner());
            }
            if (!turnCoordinator.tickOneStep(roomId, room)) {
                return RunResult.stuck(steps, room.getPhase(), lastPhase);
            }
            lastPhase = room.getPhase();
            steps++;
        }
        GameRoomState room = stateMachine.getRoom(roomId).orElseThrow();
        return RunResult.maxStepsExceeded(steps, room.getPhase());
    }

    /** @deprecated use {@link AiTurnCoordinator#tickOneStep} via {@link GamePhaseScheduler} */
    public boolean advanceOneStepPublic(String roomId) {
        GameRoomState room = stateMachine.getRoom(roomId)
                .orElseThrow(() -> new IllegalArgumentException("Room not found: " + roomId));
        return turnCoordinator.tickOneStep(roomId, room);
    }

    public record RunResult(
            Outcome outcome,
            int steps,
            GameWinner winner,
            GamePhase phase,
            GamePhase previousPhase
    ) {
        public enum Outcome {
            FINISHED,
            STUCK,
            MAX_STEPS
        }

        static RunResult finished(int steps, GameWinner winner) {
            return new RunResult(Outcome.FINISHED, steps, winner, GamePhase.GAME_OVER, null);
        }

        static RunResult stuck(int steps, GamePhase phase, GamePhase previousPhase) {
            return new RunResult(Outcome.STUCK, steps, null, phase, previousPhase);
        }

        static RunResult maxStepsExceeded(int steps, GamePhase phase) {
            return new RunResult(Outcome.MAX_STEPS, steps, null, phase, null);
        }

        public boolean isFinished() {
            return outcome == Outcome.FINISHED && winner != null;
        }
    }
}
