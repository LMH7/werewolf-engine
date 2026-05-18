package com.werewolfengine.game;

import com.werewolfengine.game.engine.GameStateMachine;
import com.werewolfengine.game.model.GamePhase;
import com.werewolfengine.game.orchestration.GamePhaseScheduler;
import com.werewolfengine.game.testsupport.GameTestAiSupport;
import com.werewolfengine.game.observability.ActionLogService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GamePhaseSchedulerTest {

    @Test
    void tickAdvancesAnnouncePhase() {
        GameStateMachine sm = new GameStateMachine();
        ActionLogService log = new ActionLogService();
        GameTestAiSupport.Harness h = GameTestAiSupport.mockOnly(sm, log);
        GamePhaseScheduler scheduler = h.phaseScheduler();

        String roomId = "sched_announce";
        sm.createRoom(roomId);
        sm.markAllReady(roomId);
        sm.startGame(roomId);

        var result = scheduler.tick(roomId);
        assertThat(result.status()).isIn("AI_STEP", "ADVANCED");
    }

    @Test
    void tickEventuallyReachesGameOver() {
        GameStateMachine sm = new GameStateMachine();
        ActionLogService log = new ActionLogService();
        GameTestAiSupport.Harness h = GameTestAiSupport.mockOnly(sm, log);
        GamePhaseScheduler scheduler = h.phaseScheduler();

        String roomId = "sched_full";
        sm.createRoom(roomId);
        sm.markAllReady(roomId);
        sm.startGame(roomId);

        for (int i = 0; i < 20_000; i++) {
            var tick = scheduler.tick(roomId);
            if ("GAME_OVER".equals(tick.status())) {
                assertThat(sm.getRoom(roomId).orElseThrow().getPhase()).isEqualTo(GamePhase.GAME_OVER);
                return;
            }
        }
        throw new AssertionError("did not finish in 20000 ticks");
    }
}
