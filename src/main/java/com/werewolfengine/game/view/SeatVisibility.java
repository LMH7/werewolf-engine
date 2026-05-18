package com.werewolfengine.game.view;

import com.werewolfengine.game.lastwords.LastWordsFlow;
import com.werewolfengine.game.model.GameActionType;
import com.werewolfengine.game.model.GamePhase;
import com.werewolfengine.game.model.GameRoomState;
import com.werewolfengine.game.model.PlayerState;
import com.werewolfengine.game.model.Role;
import com.werewolfengine.game.observability.ActionLogEntry;

/**
 * Single source for per-seat visibility (ADR-004); shared by {@link GameViews},
 * {@link com.werewolfengine.game.sync.PhaseSyncBuilder}, and {@link SeatPerceptionProjector}.
 */
public final class SeatVisibility {

    private SeatVisibility() {
    }

    public static boolean isWolf(GameRoomState room, int seat) {
        PlayerState p = room.getPlayer(seat);
        return p != null && p.getRole() == Role.WEREWOLF;
    }

    public static boolean knowsRole(GameRoomState room, int viewer, int target) {
        if (viewer == target) {
            return true;
        }
        if (isWolf(room, viewer) && isWolf(room, target)) {
            return true;
        }
        PlayerState targetPlayer = room.getPlayer(target);
        if (targetPlayer != null && targetPlayer.getRole() == Role.IDIOT && targetPlayer.isIdiotRevealed()) {
            return true;
        }
        return room.getPhase() == GamePhase.GAME_OVER;
    }

    public static boolean canAct(GameRoomState room, int viewerPlayerId) {
        PlayerState viewer = room.getPlayer(viewerPlayerId);
        if (viewer == null) {
            return false;
        }
        if (room.getPhase() == GamePhase.HUNTER_SHOOT
                && room.getHunterShooterSeat() != null
                && room.getHunterShooterSeat() == viewerPlayerId) {
            return true;
        }
        if (LastWordsFlow.isCurrentSpeaker(room, viewerPlayerId)) {
            return true;
        }
        if (!viewer.isAlive()) {
            return false;
        }
        return switch (room.getPhase()) {
            case NIGHT_WOLF -> viewer.getRole() == Role.WEREWOLF;
            case NIGHT_WITCH -> viewer.getRole() == Role.WITCH;
            case NIGHT_SEER -> viewer.getRole() == Role.SEER;
            case HUNTER_SHOOT, LAST_WORDS -> false;
            case DAY_DISCUSS -> {
                var order = room.getDiscussOrder();
                int idx = room.getDiscussIndex();
                yield !order.isEmpty() && idx < order.size() && order.get(idx) == viewerPlayerId;
            }
            case DAY_VOTE -> viewer.isCanVote();
            default -> false;
        };
    }

    public static LogVisibility visibilityForEntry(GameRoomState room, int viewerSeat, ActionLogEntry entry) {
        if (entry.playerId() == 0) {
            return visibilityForSystem(room, viewerSeat, entry);
        }
        if (entry.thinking() != null && !entry.thinking().isBlank()) {
            return entry.playerId() == viewerSeat ? LogVisibility.SELF_ONLY : LogVisibility.HIDDEN;
        }
        if (entry.playerId() != viewerSeat) {
            return visibilityForOtherPlayer(room, viewerSeat, entry);
        }
        return LogVisibility.SELF_ONLY;
    }

    private static LogVisibility visibilityForSystem(GameRoomState room, int viewerSeat, ActionLogEntry entry) {
        String msg = entry.content();
        if (msg == null) {
            return LogVisibility.HIDDEN;
        }
        if (msg.startsWith("WOLF_KILL_RESOLVED")) {
            return isWolf(room, viewerSeat) ? LogVisibility.WOLF_ONLY : LogVisibility.HIDDEN;
        }
        if (msg.startsWith("WOLF_") || msg.contains(" votes=")) {
            return LogVisibility.HIDDEN;
        }
        if (msg.startsWith("NIGHT_DEATHS")
                || msg.startsWith("EXILE_ANNOUNCED")
                || msg.startsWith("IDIOT_REVEALED")
                || msg.startsWith("GAME_OVER")
                || msg.startsWith("HUNTER_SHOT")
                || msg.startsWith("VOTE_")
                || msg.startsWith("EXILE_RESOLVED")) {
            return LogVisibility.PUBLIC;
        }
        if (msg.equals("advanceDayAnnounce")) {
            return LogVisibility.HIDDEN;
        }
        return LogVisibility.PUBLIC;
    }

    private static LogVisibility visibilityForOtherPlayer(GameRoomState room, int viewerSeat, ActionLogEntry entry) {
        GameActionType action = entry.action();
        if (action == null) {
            return LogVisibility.HIDDEN;
        }
        return switch (action) {
            case WOLF_CHAT -> isWolf(room, viewerSeat) ? LogVisibility.WOLF_ONLY : LogVisibility.HIDDEN;
            case SPEAK, VOTE, SKIP_VOTE, SHOOT -> LogVisibility.PUBLIC;
            case KILL, SAVE, POISON, CHECK, SKIP -> LogVisibility.HIDDEN;
            case SKIP_SPEAK -> LogVisibility.PUBLIC;
            default -> LogVisibility.HIDDEN;
        };
    }
}
