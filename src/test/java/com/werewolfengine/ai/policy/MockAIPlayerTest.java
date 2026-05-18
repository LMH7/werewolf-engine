package com.werewolfengine.ai.policy;

import com.werewolfengine.game.engine.GameStateMachine;
import com.werewolfengine.game.model.GameActionType;
import com.werewolfengine.game.model.GamePhase;
import com.werewolfengine.game.model.GameRoomState;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MockAIPlayerTest {

    private final GameStateMachine stateMachine = new GameStateMachine();
    private final MockAIPlayer mock = new MockAIPlayer();

    @Test
    void firstWolfSendsWolfChatBeforeKillWhenChannelClosed() {
        String roomId = "wolf_chat_mock";
        stateMachine.createRoom(roomId);
        stateMachine.markAllReady(roomId);
        stateMachine.startGame(roomId);
        GameRoomState room = stateMachine.getRoom(roomId).orElseThrow();
        assertThat(room.getPhase()).isEqualTo(GamePhase.NIGHT_WOLF);
        assertThat(room.isWolfChatInPhase()).isFalse();

        int firstWolf = room.aliveWolfIds().stream().sorted().findFirst().orElseThrow();
        var intent = mock.decide(room, firstWolf);
        assertThat(intent).isPresent();
        assertThat(intent.get().action()).isEqualTo(GameActionType.WOLF_CHAT);
        assertThat(intent.get().content()).isNotBlank();
    }
}
