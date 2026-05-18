package com.werewolfengine.game.observability;

import com.werewolfengine.game.model.GamePhase;
import com.werewolfengine.game.model.GameRoomState;
import com.werewolfengine.game.model.GameWinner;

import java.util.stream.Collectors;

/** P0 system lines for seat perception (ADR-004 §4.2). */
public final class PerceptionLogEvents {

    private PerceptionLogEvents() {
    }

    public static void nightDeaths(ActionLogService log, GameRoomState room) {
        if (log == null || room == null) {
            return;
        }
        String seats = room.getLastNightDeaths().stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
        log.recordSystemEvent(
                room.getRoomId(),
                room.getRound(),
                GamePhase.NIGHT_DEATH_ANNOUNCE,
                "NIGHT_DEATHS seats=" + seats,
                null
        );
    }

    public static void exileAnnounced(ActionLogService log, GameRoomState room, int seat) {
        if (log == null || room == null) {
            return;
        }
        log.recordSystemEvent(
                room.getRoomId(),
                room.getRound(),
                GamePhase.EXILE_DEATH_ANNOUNCE,
                "EXILE_ANNOUNCED seat=" + seat,
                seat
        );
    }

    public static void idiotRevealed(ActionLogService log, GameRoomState room, int seat) {
        if (log == null || room == null) {
            return;
        }
        log.recordSystemEvent(
                room.getRoomId(),
                room.getRound(),
                room.getPhase(),
                "IDIOT_REVEALED seat=" + seat,
                seat
        );
    }

    public static void gameOver(ActionLogService log, GameRoomState room, GameWinner winner) {
        if (log == null || room == null || winner == null) {
            return;
        }
        log.recordSystemEvent(
                room.getRoomId(),
                room.getRound(),
                GamePhase.GAME_OVER,
                "GAME_OVER winner=" + winner.name(),
                null
        );
    }
}
