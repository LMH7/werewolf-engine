package com.werewolfengine.game;

import com.werewolfengine.game.engine.GameStateMachine;
import com.werewolfengine.game.model.ActionErrorCode;
import com.werewolfengine.game.model.GameActionCommand;
import com.werewolfengine.game.model.GameActionType;
import com.werewolfengine.game.model.GamePhase;
import com.werewolfengine.game.model.GameRoomState;
import com.werewolfengine.game.model.GameWinner;
import com.werewolfengine.game.model.PlayerState;
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

    /** 狼刀后：预言家 → 女巫 → 公布死讯（测试内自动 advance）。 */
    private void completeLastWords(String roomId) {
        GameRoomState room = sm.getRoom(roomId).orElseThrow();
        while (room.getPhase() == GamePhase.LAST_WORDS) {
            int speaker = room.getLastWordsOrder().get(room.getLastWordsIndex());
            sm.handleAction(roomId,
                    new GameActionCommand(speaker, GameActionType.SKIP_SPEAK, null, GamePhase.LAST_WORDS));
            room = sm.getRoom(roomId).orElseThrow();
        }
    }

    private void completeNightToAnnounce(String roomId, GameRoomState room) {
        if (room.getPhase() == GamePhase.NIGHT_SEER && seerAlive(room)) {
            int seer = room.seerSeat();
            int checkTarget = room.alivePlayerIds().stream()
                    .filter(id -> id != seer)
                    .findFirst()
                    .orElseThrow();
            sm.handleAction(roomId,
                    new GameActionCommand(seer, GameActionType.CHECK, checkTarget, GamePhase.NIGHT_SEER));
            room = sm.getRoom(roomId).orElseThrow();
        }
        if (room.getPhase() == GamePhase.NIGHT_WITCH && witchAlive(room)) {
            sm.handleAction(roomId,
                    new GameActionCommand(room.witchSeat(), GameActionType.SKIP, null, GamePhase.NIGHT_WITCH));
        }
    }

    private static boolean seerAlive(GameRoomState room) {
        int ss = room.seerSeat();
        return ss > 0 && room.getPlayer(ss) != null && room.getPlayer(ss).isAlive();
    }

    private static int firstAliveSeatWithRole(GameRoomState room, Role role) {
        return room.alivePlayerIds().stream()
                .filter(id -> room.getPlayer(id).getRole() == role)
                .findFirst()
                .orElseThrow();
    }

    private static boolean witchAlive(GameRoomState room) {
        int ws = room.witchSeat();
        return ws > 0 && room.getPlayer(ws) != null && room.getPlayer(ws).isAlive();
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
    void allWolvesVote_movesToSeerFirst() {
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
        if (seerAlive(room)) {
            assertThat(room.getPhase()).isEqualTo(GamePhase.NIGHT_SEER);
        } else if (witchAlive(room)) {
            assertThat(room.getPhase()).isEqualTo(GamePhase.NIGHT_WITCH);
        } else {
            assertThat(room.getPhase()).isIn(GamePhase.NIGHT_DEATH_ANNOUNCE, GamePhase.GAME_OVER);
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
    void firstNight_afterAnnounce_reachesDayDiscuss() {
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
        completeNightToAnnounce(roomId, room);

        room = sm.getRoom(roomId).orElseThrow();
        assertThat(room.getPhase()).isEqualTo(GamePhase.NIGHT_DEATH_ANNOUNCE);
        assertThat(room.getPlayer(villager).isAlive()).isFalse();

        sm.advanceDayAnnounce(roomId);
        room = sm.getRoom(roomId).orElseThrow();
        assertThat(room.getPhase()).isEqualTo(GamePhase.LAST_WORDS);
        assertThat(room.getLastWordsOrder()).containsExactly(villager);
        completeLastWords(roomId);
        room = sm.getRoom(roomId).orElseThrow();
        assertThat(room.getPhase()).isEqualTo(GamePhase.DAY_DISCUSS);
        assertThat(room.getHunterShooterSeat()).isNull();
    }

    @Test
    void secondNightDeath_skipsLastWords() {
        String roomId = "r_night2";
        sm.createRoom(roomId);
        sm.markAllReady(roomId);
        sm.startGame(roomId);
        GameRoomState room = sm.getRoom(roomId).orElseThrow();
        int villager = firstAliveSeatWithRole(room, Role.VILLAGER);

        allWolvesKill(sm, roomId, room, villager);
        completeNightToAnnounce(roomId, sm.getRoom(roomId).orElseThrow());
        sm.advanceDayAnnounce(roomId);
        completeLastWords(roomId);

        room = sm.getRoom(roomId).orElseThrow();
        room.setRound(2);
        room.setPhase(GamePhase.NIGHT_WOLF);
        int victim = firstAliveSeatWithRole(room, Role.VILLAGER);
        allWolvesKill(sm, roomId, room, victim);
        completeNightToAnnounce(roomId, sm.getRoom(roomId).orElseThrow());

        room = sm.getRoom(roomId).orElseThrow();
        assertThat(room.getRound()).isEqualTo(2);
        assertThat(room.getPhase()).isEqualTo(GamePhase.NIGHT_DEATH_ANNOUNCE);

        sm.advanceDayAnnounce(roomId);
        room = sm.getRoom(roomId).orElseThrow();
        assertThat(room.getPhase()).isNotEqualTo(GamePhase.LAST_WORDS);
        assertThat(room.getPhase()).isEqualTo(GamePhase.DAY_DISCUSS);
    }

    @Test
    void hunterKilledAtNight_entersHunterShootOnlyAfterAnnounce() {
        String roomId = "r_hunter_night";
        sm.createRoom(roomId);
        sm.markAllReady(roomId);
        sm.startGame(roomId);

        GameRoomState room = sm.getRoom(roomId).orElseThrow();
        int hunter = room.getPlayers().values().stream()
                .filter(p -> p.getRole() == Role.HUNTER)
                .map(com.werewolfengine.game.model.PlayerState::getPlayerId)
                .findFirst()
                .orElseThrow();

        allWolvesKill(sm, roomId, room, hunter);
        room = sm.getRoom(roomId).orElseThrow();
        completeNightToAnnounce(roomId, room);

        room = sm.getRoom(roomId).orElseThrow();
        assertThat(room.getPhase()).isEqualTo(GamePhase.NIGHT_DEATH_ANNOUNCE);
        assertThat(room.getPendingHunterAfterAnnounce()).isEqualTo(hunter);
        assertThat(room.getHunterShooterSeat()).isNull();

        sm.advanceDayAnnounce(roomId);
        room = sm.getRoom(roomId).orElseThrow();
        assertThat(room.getPhase()).isEqualTo(GamePhase.LAST_WORDS);
        completeLastWords(roomId);
        room = sm.getRoom(roomId).orElseThrow();
        assertThat(room.getPhase()).isEqualTo(GamePhase.HUNTER_SHOOT);
        assertThat(room.getHunterShooterSeat()).isEqualTo(hunter);
    }

    @Test
    void exiledPlayer_hasLastWordsBeforeCheckWin() {
        String roomId = "r_exile_words";
        sm.createRoom(roomId);
        sm.markAllReady(roomId);
        sm.startGame(roomId);
        GameRoomState room = sm.getRoom(roomId).orElseThrow();
        int villager = firstAliveSeatWithRole(room, Role.VILLAGER);

        room.setPhase(GamePhase.DAY_VOTE);
        for (int id : room.alivePlayerIds()) {
            PlayerState p = room.getPlayer(id);
            if (p.isCanVote()) {
                sm.handleAction(roomId,
                        new GameActionCommand(id, GameActionType.VOTE, villager, GamePhase.DAY_VOTE));
            }
        }

        room = sm.getRoom(roomId).orElseThrow();
        assertThat(room.getPhase()).isEqualTo(GamePhase.EXILE_DEATH_ANNOUNCE);
        assertThat(room.getPlayer(villager).isAlive()).isFalse();

        sm.advanceDayAnnounce(roomId);
        room = sm.getRoom(roomId).orElseThrow();
        assertThat(room.getPhase()).isEqualTo(GamePhase.LAST_WORDS);
        assertThat(room.getLastWordsOrder()).containsExactly(villager);

        sm.handleAction(roomId,
                new GameActionCommand(villager, GameActionType.SKIP_SPEAK, null, GamePhase.LAST_WORDS));
        room = sm.getRoom(roomId).orElseThrow();
        assertThat(room.getPhase()).isEqualTo(GamePhase.NIGHT_WOLF);
        assertThat(room.getRound()).isEqualTo(2);
    }

    @Test
    void hunterExiled_goesToExileDeathAnnounce() {
        String roomId = "r_hunter_exile";
        sm.createRoom(roomId);
        sm.markAllReady(roomId);
        sm.startGame(roomId);
        GameRoomState room = sm.getRoom(roomId).orElseThrow();
        int hunter = seatOf(room, Role.HUNTER);

        room.setPhase(GamePhase.DAY_VOTE);
        for (int id : room.alivePlayerIds()) {
            PlayerState p = room.getPlayer(id);
            if (p.isCanVote()) {
                sm.handleAction(roomId,
                        new GameActionCommand(id, GameActionType.VOTE, hunter, GamePhase.DAY_VOTE));
            }
        }

        room = sm.getRoom(roomId).orElseThrow();
        assertThat(room.getPhase()).isEqualTo(GamePhase.EXILE_DEATH_ANNOUNCE);
        assertThat(room.getPendingHunterAfterAnnounce()).isEqualTo(hunter);
        assertThat(room.getPlayer(hunter).isAlive()).isTrue();
    }

    @Test
    void lastGodHunterExiled_wolvesWinWithoutHunterShoot() {
        String roomId = "r_last_god_vote";
        sm.createRoom(roomId);
        sm.markAllReady(roomId);
        sm.startGame(roomId);
        GameRoomState room = sm.getRoom(roomId).orElseThrow();
        int hunter = seatOf(room, Role.HUNTER);
        killAllGodsExcept(room, hunter);

        room.setPhase(GamePhase.DAY_VOTE);
        for (int id : room.alivePlayerIds()) {
            PlayerState p = room.getPlayer(id);
            if (p.isCanVote()) {
                sm.handleAction(roomId,
                        new GameActionCommand(id, GameActionType.VOTE, hunter, GamePhase.DAY_VOTE));
            }
        }

        room = sm.getRoom(roomId).orElseThrow();
        assertThat(room.getPhase()).isEqualTo(GamePhase.GAME_OVER);
        assertThat(room.getWinner()).isEqualTo(GameWinner.WEREWOLVES);
        assertThat(room.getHunterShooterSeat()).isNull();
    }

    @Test
    void lastGodHunterKilledAtNight_wolvesWinNoHunterPhase() {
        String roomId = "r_last_god_night";
        sm.createRoom(roomId);
        sm.markAllReady(roomId);
        sm.startGame(roomId);
        GameRoomState room = sm.getRoom(roomId).orElseThrow();
        int hunter = seatOf(room, Role.HUNTER);
        killAllGodsExcept(room, hunter);

        allWolvesKill(sm, roomId, room, hunter);
        completeNightToAnnounce(roomId, sm.getRoom(roomId).orElseThrow());

        room = sm.getRoom(roomId).orElseThrow();
        assertThat(room.getPhase()).isEqualTo(GamePhase.GAME_OVER);
        assertThat(room.getWinner()).isEqualTo(GameWinner.WEREWOLVES);
        assertThat(room.getPendingHunterAfterAnnounce()).isNull();
    }

    @Test
    void lastVillagerKilledAtNight_wolvesWin() {
        String roomId = "r_last_villager";
        sm.createRoom(roomId);
        sm.markAllReady(roomId);
        sm.startGame(roomId);
        GameRoomState room = sm.getRoom(roomId).orElseThrow();
        final GameRoomState r0 = room;
        final int lastVillager = r0.alivePlayerIds().stream()
                .filter(id -> r0.getPlayer(id).getRole() == Role.VILLAGER)
                .findFirst()
                .orElseThrow();
        for (PlayerState p : room.getPlayers().values()) {
            if (p.getRole() == Role.VILLAGER && p.getPlayerId() != lastVillager) {
                p.setAlive(false);
            } else if (p.getRole() != null && p.getRole() != Role.VILLAGER && p.getRole() != Role.WEREWOLF) {
                p.setAlive(false);
            }
        }

        allWolvesKill(sm, roomId, room, lastVillager);
        completeNightToAnnounce(roomId, sm.getRoom(roomId).orElseThrow());

        room = sm.getRoom(roomId).orElseThrow();
        assertThat(room.getPhase()).isEqualTo(GamePhase.GAME_OVER);
        assertThat(room.getWinner()).isEqualTo(GameWinner.WEREWOLVES);
    }

    private static int seatOf(GameRoomState room, Role role) {
        return room.getPlayers().values().stream()
                .filter(p -> p.getRole() == role)
                .map(PlayerState::getPlayerId)
                .findFirst()
                .orElseThrow();
    }

    private static void killAllGodsExcept(GameRoomState room, int keepSeat) {
        for (PlayerState p : room.getPlayers().values()) {
            if (p.getRole() == Role.SEER || p.getRole() == Role.WITCH || p.getRole() == Role.IDIOT) {
                p.setAlive(false);
            }
            if (p.getRole() == Role.HUNTER && p.getPlayerId() != keepSeat) {
                p.setAlive(false);
            }
        }
    }
}
