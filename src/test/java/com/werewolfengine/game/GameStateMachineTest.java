package com.werewolfengine.game;

import com.werewolfengine.game.model.ActionErrorCode;
import com.werewolfengine.game.model.GameActionCommand;
import com.werewolfengine.game.model.GameActionType;
import com.werewolfengine.game.model.GamePhase;
import com.werewolfengine.game.model.GameRoomState;
import com.werewolfengine.game.model.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GameStateMachineTest {

    private GameStateMachine sm;

    @BeforeEach
    void setUp() {
        sm = new GameStateMachine();
    }

    private static void allWolvesKill(GameStateMachine sm, String roomId, GameRoomState room, int target) {
        for (int w : room.aliveWolfIds()) {
            sm.handleAction(roomId,
                    new GameActionCommand(w, GameActionType.KILL, target, GamePhase.NIGHT_WOLF));
        }
    }

    @Test
    void startGame_advancesToNightWolf() {
        String roomId = "r_test";
        sm.createRoom(roomId);
        sm.markAllReady(roomId);

        GameStateMachine.StartGameResult result = sm.startGame(roomId);

        assertThat(result.success()).isTrue();
        assertThat(result.phase()).isEqualTo(GamePhase.NIGHT_WOLF);

        GameRoomState room = sm.getRoom(roomId).orElseThrow();
        assertThat(room.getPhase()).isEqualTo(GamePhase.NIGHT_WOLF);
        assertThat(room.getRound()).isEqualTo(1);
        assertThat(room.isWolfChatInPhase()).isFalse();
        assertThat(room.aliveWolfIds()).hasSize(4);
    }

    @Test
    void killOnNonWolf_succeedsWithoutWolfChat() {
        String roomId = "r_kill";
        sm.createRoom(roomId);
        sm.markAllReady(roomId);
        sm.startGame(roomId);
        GameRoomState room = sm.getRoom(roomId).orElseThrow();

        int wolf = room.aliveWolfIds().getFirst();
        int target = room.alivePlayerIds().stream()
                .filter(id -> room.getPlayer(id).getRole() != Role.WEREWOLF)
                .findFirst()
                .orElseThrow();

        GameStateMachine.HandleActionResult result = sm.handleAction(roomId,
                new GameActionCommand(wolf, GameActionType.KILL, target, GamePhase.NIGHT_WOLF));

        assertThat(result.ack().success()).isTrue();
        assertThat(room.getWolfKillVotes()).containsEntry(wolf, target);
        assertThat(room.getPhase()).isEqualTo(GamePhase.NIGHT_WOLF);
    }

    @Test
    void allWolvesVote_movesToWitchOrSeer() {
        String roomId = "r_wolf_done";
        sm.createRoom(roomId);
        sm.markAllReady(roomId);
        sm.startGame(roomId);
        final GameRoomState r0 = sm.getRoom(roomId).orElseThrow();
        int target = r0.alivePlayerIds().stream()
                .filter(id -> r0.getPlayer(id).getRole() != Role.WEREWOLF)
                .findFirst()
                .orElseThrow();

        allWolvesKill(sm, roomId, r0, target);

        GameRoomState room = sm.getRoom(roomId).orElseThrow();
        assertThat(room.getPendingWolfKillTarget()).isEqualTo(target);
        if (room.witchSeat() > 0 && room.getPlayer(room.witchSeat()).isAlive()) {
            assertThat(room.getPhase()).isEqualTo(GamePhase.NIGHT_WITCH);
        } else {
            assertThat(room.getPhase()).isIn(GamePhase.NIGHT_SEER, GamePhase.DAY_DISCUSS, GamePhase.GAME_OVER);
        }
    }

    @Test
    void killOnWolf_withoutChat_returnsWolfChatRequired() {
        String roomId = "r_r17a";
        sm.createRoom(roomId);
        sm.markAllReady(roomId);
        sm.startGame(roomId);
        GameRoomState room = sm.getRoom(roomId).orElseThrow();

        int wolf = room.aliveWolfIds().getFirst();
        int wolfTarget = room.aliveWolfIds().get(1);

        GameStateMachine.HandleActionResult result = sm.handleAction(roomId,
                new GameActionCommand(wolf, GameActionType.KILL, wolfTarget, GamePhase.NIGHT_WOLF));

        assertThat(result.ack().success()).isFalse();
        assertThat(result.ack().code()).isEqualTo(ActionErrorCode.WOLF_CHAT_REQUIRED);
    }

    @Test
    void killOnWolf_afterWolfChat_succeeds() {
        String roomId = "r_chat";
        sm.createRoom(roomId);
        sm.markAllReady(roomId);
        sm.startGame(roomId);
        GameRoomState room = sm.getRoom(roomId).orElseThrow();

        int wolf = room.aliveWolfIds().getFirst();
        int wolfTarget = room.aliveWolfIds().get(1);

        sm.handleAction(roomId, new GameActionCommand(wolf, GameActionType.WOLF_CHAT, null, GamePhase.NIGHT_WOLF));
        GameStateMachine.HandleActionResult result = sm.handleAction(roomId,
                new GameActionCommand(wolf, GameActionType.KILL, wolfTarget, GamePhase.NIGHT_WOLF));

        assertThat(result.ack().success()).isTrue();
        assertThat(room.isWolfChatInPhase()).isTrue();
        assertThat(room.getWolfKillVotes()).containsEntry(wolf, wolfTarget);
    }

    @Test
    void firstNight_witchSkip_seerCheck_reachesDayDiscuss() {
        String roomId = "r_night1";
        sm.createRoom(roomId);
        sm.markAllReady(roomId);
        sm.startGame(roomId);
        final GameRoomState r0 = sm.getRoom(roomId).orElseThrow();
        int villager = r0.alivePlayerIds().stream()
                .filter(id -> r0.getPlayer(id).getRole() == Role.VILLAGER)
                .findFirst()
                .orElseThrow();

        allWolvesKill(sm, roomId, r0, villager);
        GameRoomState room = sm.getRoom(roomId).orElseThrow();

        if (room.getPhase() == GamePhase.NIGHT_WITCH) {
            int witch = room.witchSeat();
            sm.handleAction(roomId,
                    new GameActionCommand(witch, GameActionType.SKIP, null, GamePhase.NIGHT_WITCH));
            room = sm.getRoom(roomId).orElseThrow();
        }

        if (room.getPhase() == GamePhase.NIGHT_SEER) {
            int seer = room.seerSeat();
            int checkTarget = room.alivePlayerIds().stream()
                    .filter(id -> id != seer)
                    .findFirst()
                    .orElseThrow();
            sm.handleAction(roomId,
                    new GameActionCommand(seer, GameActionType.CHECK, checkTarget, GamePhase.NIGHT_SEER));
            room = sm.getRoom(roomId).orElseThrow();
        }

        assertThat(room.getPhase()).isIn(GamePhase.DAY_DISCUSS, GamePhase.HUNTER_SHOOT, GamePhase.GAME_OVER);
    }
}
