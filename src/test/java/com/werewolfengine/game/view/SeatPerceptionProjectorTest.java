package com.werewolfengine.game.view;

import com.werewolfengine.game.model.GameActionType;
import com.werewolfengine.game.model.GamePhase;
import com.werewolfengine.game.model.GameRoomState;
import com.werewolfengine.game.model.PlayerState;
import com.werewolfengine.game.model.Role;
import com.werewolfengine.game.observability.ActionLogEntry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SeatPerceptionProjectorTest {

    @Test
    void m3a_villagerHidesWolfOnlyAndForeignThinking() {
        GameRoomState room = roomWithRoles(1, Role.VILLAGER, 2, Role.WEREWOLF, 3, Role.WEREWOLF);
        List<ActionLogEntry> log = List.of(
                entry(room, 1, Role.VILLAGER, GamePhase.DAY_DISCUSS, GameActionType.SPEAK, "我是好人"),
                entry(room, 2, Role.WEREWOLF, GamePhase.NIGHT_WOLF, GameActionType.WOLF_CHAT, "集合刀3"),
                system(room, GamePhase.NIGHT_WOLF, "WOLF_KILL_RESOLVED target=3 votes={2=3,3=3}"),
                thinking(room, 3, Role.WEREWOLF, "secret plan")
        );

        SeatPerceptionSlice slice = SeatPerceptionProjector.project(room, 1, log);

        assertThat(slice.events()).extracting(VisibleEvent::kind)
                .doesNotContain(PerceptionEventKind.WOLF_CHAT, PerceptionEventKind.WOLF_KILL_RESOLVED);
        assertThat(slice.events()).noneMatch(e -> e.summary().contains("WEREWOLF"));
        assertThat(slice.events()).noneMatch(e -> e.summary().contains("secret"));
    }

    @Test
    void m3aWolf_seesWolfChatAndKillWithoutVotes() {
        GameRoomState room = roomWithRoles(1, Role.VILLAGER, 2, Role.WEREWOLF, 3, Role.WEREWOLF);
        List<ActionLogEntry> log = List.of(
                entry(room, 2, Role.WEREWOLF, GamePhase.NIGHT_WOLF, GameActionType.WOLF_CHAT, "刀3"),
                system(room, GamePhase.NIGHT_WOLF, "WOLF_KILL_RESOLVED target=3 votes={2=3}")
        );

        SeatPerceptionSlice slice = SeatPerceptionProjector.project(room, 2, log);

        assertThat(slice.events()).extracting(VisibleEvent::kind)
                .contains(PerceptionEventKind.WOLF_CHAT, PerceptionEventKind.WOLF_KILL_RESOLVED);
        assertThat(slice.events()).noneMatch(e -> e.summary().contains("votes="));
    }

    @Test
    void m3c_wolfAndVillagerWolfChatCounts() {
        GameRoomState room = roomWithRoles(1, Role.VILLAGER, 2, Role.WEREWOLF);
        List<ActionLogEntry> log = List.of(
                entry(room, 2, Role.WEREWOLF, GamePhase.NIGHT_WOLF, GameActionType.WOLF_CHAT, "a"),
                entry(room, 2, Role.WEREWOLF, GamePhase.NIGHT_WOLF, GameActionType.WOLF_CHAT, "b")
        );

        long wolfCount = SeatPerceptionProjector.project(room, 2, log).events().stream()
                .filter(e -> e.kind() == PerceptionEventKind.WOLF_CHAT)
                .count();
        long villagerCount = SeatPerceptionProjector.project(room, 1, log).events().stream()
                .filter(e -> e.kind() == PerceptionEventKind.WOLF_CHAT)
                .count();

        assertThat(wolfCount).isGreaterThanOrEqualTo(1);
        assertThat(villagerCount).isZero();
    }

    @Test
    void nightDeathsAndVoteVisibleToAll() {
        GameRoomState room = roomWithRoles(1, Role.VILLAGER, 2, Role.VILLAGER);
        List<ActionLogEntry> log = List.of(
                system(room, GamePhase.NIGHT_DEATH_ANNOUNCE, "NIGHT_DEATHS seats=2"),
                entry(room, 1, Role.VILLAGER, GamePhase.DAY_VOTE, GameActionType.VOTE, null, 2)
        );

        SeatPerceptionSlice slice = SeatPerceptionProjector.project(room, 1, log);

        assertThat(slice.events()).extracting(VisibleEvent::kind)
                .contains(PerceptionEventKind.NIGHT_DEATH, PerceptionEventKind.PUBLIC_VOTE);
    }

    private static GameRoomState roomWithRoles(Object... seatRolePairs) {
        GameRoomState room = new GameRoomState("test-room");
        room.setStatus(com.werewolfengine.game.model.RoomStatus.PLAYING);
        room.setPhase(GamePhase.DAY_DISCUSS);
        room.setRound(1);
        for (int i = 0; i < seatRolePairs.length; i += 2) {
            int seat = (Integer) seatRolePairs[i];
            Role role = (Role) seatRolePairs[i + 1];
            PlayerState p = new PlayerState(seat);
            p.setRole(role);
            p.setAlive(true);
            room.getPlayers().put(seat, p);
        }
        return room;
    }

    private static ActionLogEntry entry(
            GameRoomState room,
            int seat,
            Role role,
            GamePhase phase,
            GameActionType action,
            String content
    ) {
        return entry(room, seat, role, phase, action, content, null);
    }

    private static ActionLogEntry entry(
            GameRoomState room,
            int seat,
            Role role,
            GamePhase phase,
            GameActionType action,
            String content,
            Integer target
    ) {
        return new ActionLogEntry(
                room.getRoomId(),
                room.getRound(),
                phase,
                seat,
                role,
                action,
                target,
                content,
                true,
                0L,
                null,
                null
        );
    }

    private static ActionLogEntry thinking(GameRoomState room, int seat, Role role, String thinking) {
        return new ActionLogEntry(
                room.getRoomId(),
                room.getRound(),
                GamePhase.NIGHT_WOLF,
                seat,
                role,
                GameActionType.KILL,
                null,
                null,
                true,
                0L,
                thinking,
                "mock"
        );
    }

    private static ActionLogEntry system(GameRoomState room, GamePhase phase, String message) {
        return new ActionLogEntry(
                room.getRoomId(),
                room.getRound(),
                phase,
                0,
                null,
                null,
                null,
                message,
                true,
                0L,
                null,
                null
        );
    }
}
