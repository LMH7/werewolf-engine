package com.werewolfengine.ai.api;

import com.werewolfengine.game.engine.GameStateMachine;
import com.werewolfengine.game.testsupport.GameTestAiSupport;
import com.werewolfengine.game.model.GameRoomState;
import com.werewolfengine.game.observability.ActionLogService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AIServiceHumanSeatTest {

    @Test
    void decideReturnsEmptyForHumanSeat() {
        GameStateMachine sm = new GameStateMachine();
        String roomId = "human_seat";
        sm.createRoom(roomId);
        sm.markAllReady(roomId);
        sm.startGame(roomId);
        GameRoomState room = sm.getRoom(roomId).orElseThrow();
        int wolf = room.aliveWolfIds().getFirst();
        room.getPlayer(wolf).setHumanUserId(10_001L);

        AIService ai = GameTestAiSupport.disabledAiService();
        assertThat(ai.decide(room, wolf)).isEmpty();
    }

    @Test
    void turnCoordinatorSkipsHumanSeat() {
        GameStateMachine sm = new GameStateMachine();
        ActionLogService log = new ActionLogService();
        var h = GameTestAiSupport.mockOnly(sm, log);
        String roomId = "human_skip";
        sm.createRoom(roomId);
        sm.markAllReady(roomId);
        sm.startGame(roomId);
        GameRoomState room = sm.getRoom(roomId).orElseThrow();
        for (int wolfId : room.aliveWolfIds()) {
            room.getPlayer(wolfId).setHumanUserId(1000L + wolfId);
        }
        assertThat(h.turnCoordinator().tickOneStep(roomId, room)).isFalse();
    }
}
