package com.werewolfengine.game;

import com.werewolfengine.game.engine.GameEngineService;
import com.werewolfengine.game.engine.GameStateMachine;
import com.werewolfengine.game.model.GamePhase;
import com.werewolfengine.game.orchestration.MockGameRunner;
import com.werewolfengine.game.testsupport.GameTestAiSupport;
import com.werewolfengine.game.model.GameRoomState;
import com.werewolfengine.game.model.GameWinner;
import com.werewolfengine.game.model.RoomStatus;
import com.werewolfengine.game.observability.ActionLogService;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A-01 — 12 座 Mock AI 无人干预跑通至 {@link GamePhase#GAME_OVER}.
 */
class MockAIFullGameTest {

    private final ActionLogService actionLog = new ActionLogService();
    private final GameStateMachine stateMachine = new GameStateMachine(actionLog);
    private final GameTestAiSupport.Harness harness = GameTestAiSupport.mockOnly(stateMachine, actionLog);
    private final MockGameRunner runner = harness.mockGameRunner();

    @RepeatedTest(5)
    void mockAiPlaysFullGameToWinner() {
        String roomId = "mock_full_" + System.nanoTime();
        stateMachine.createRoom(roomId);
        stateMachine.markAllReady(roomId);
        var start = stateMachine.startGame(roomId);
        assertThat(start.success()).isTrue();

        MockGameRunner.RunResult result = runner.runUntilGameOver(roomId);
        GameRoomState room = stateMachine.getRoom(roomId).orElseThrow();

        assertThat(result.outcome())
                .as("steps=%d phase=%s", result.steps(), result.phase())
                .isEqualTo(MockGameRunner.RunResult.Outcome.FINISHED);
        assertThat(room.getPhase()).isEqualTo(GamePhase.GAME_OVER);
        assertThat(room.getStatus()).isEqualTo(RoomStatus.ENDED);
        assertThat(room.getWinner()).isIn(GameWinner.WEREWOLVES, GameWinner.VILLAGERS);
        assertThat(result.winner()).isEqualTo(room.getWinner());
        assertThat(result.steps()).isGreaterThan(50);
    }

    @Test
    void gameEngineFacadeRunsMockAutoPlay() {
        GameStateMachine sm = new GameStateMachine(actionLog);
        GameTestAiSupport.Harness h = GameTestAiSupport.mockOnly(sm, actionLog);
        GameEngineService engine = new GameEngineService(
                sm,
                h.aiService(),
                h.mockGameRunner(),
                actionLog,
                h.phaseScheduler()
        );
        String roomId = "mock_facade";
        engine.createDevRoom(roomId);
        engine.startGame(roomId);

        MockGameRunner.RunResult result = engine.runMockAutoPlay(roomId);
        assertThat(result.isFinished()).isTrue();
    }
}
