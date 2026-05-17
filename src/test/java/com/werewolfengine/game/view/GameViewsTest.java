package com.werewolfengine.game.view;

import com.werewolfengine.game.engine.GameStateMachine;
import com.werewolfengine.game.sync.PhaseSyncBuilder;
import com.werewolfengine.game.model.GamePhase;
import com.werewolfengine.game.model.GameRoomState;
import com.werewolfengine.game.model.Role;
import com.werewolfengine.message.payload.PhaseSyncPayload;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GameViewsTest {

    @Test
    void canActMatchesPhaseSyncBuilder() {
        GameStateMachine sm = new GameStateMachine();
        String roomId = "gv_sync";
        sm.createRoom(roomId);
        sm.markAllReady(roomId);
        sm.startGame(roomId);
        GameRoomState room = sm.getRoom(roomId).orElseThrow();
        assertThat(room.getPhase()).isEqualTo(GamePhase.NIGHT_WOLF);

        for (int seat = 1; seat <= 12; seat++) {
            GameView view = GameViews.forSeat(room, seat);
            PhaseSyncPayload sync = PhaseSyncBuilder.forPlayer(room, seat);
            assertThat(view.canAct()).isEqualTo(sync.canAct());
        }
    }

    @Test
    void wolfSeesTeammatesInGameView() {
        GameStateMachine sm = new GameStateMachine();
        String roomId = "gv_wolf";
        sm.createRoom(roomId);
        sm.markAllReady(roomId);
        sm.startGame(roomId);
        GameRoomState room = sm.getRoom(roomId).orElseThrow();
        int wolf = room.aliveWolfIds().getFirst();
        GameView view = GameViews.forSeat(room, wolf);
        assertThat(view.yourRole()).isEqualTo(Role.WEREWOLF);
        assertThat(view.wolfTeammates()).isNotEmpty();
        assertThat(view.wolfTeammates()).doesNotContain(wolf);
    }
}
