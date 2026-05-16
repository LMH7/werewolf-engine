package com.werewolfengine.game;

import com.werewolfengine.game.model.GamePhase;
import com.werewolfengine.game.model.GameRoomState;
import com.werewolfengine.game.model.PlayerState;
import com.werewolfengine.game.model.Role;
import com.werewolfengine.game.model.SeerCheckResult;
import com.werewolfengine.game.model.SpeakDirection;
import com.werewolfengine.message.payload.PhaseSyncPayload;

import java.util.ArrayList;
import java.util.List;

/** Builds per-seat PHASE_SYNC payloads for gateway (B). */
public final class PhaseSyncBuilder {

    private PhaseSyncBuilder() {
    }

    public static PhaseSyncPayload forPlayer(GameRoomState room, int viewerPlayerId) {
        PlayerState viewer = room.getPlayer(viewerPlayerId);
        Role yourRole = viewer != null ? viewer.getRole() : null;
        GamePhase phase = room.getPhase();

        boolean canAct = canAct(room, viewer, viewerPlayerId);
        Boolean wolfChat = phase == GamePhase.NIGHT_WOLF ? room.isWolfChatInPhase() : null;

        Boolean idiotRev = idiotRevealedForViewer(viewer);

        Integer witchAntidote = null;
        Integer witchPoison = null;
        Integer wolfKill = null;
        if (phase == GamePhase.NIGHT_WITCH && viewer != null && viewer.getRole() == Role.WITCH) {
            witchAntidote = room.isWitchAntidoteRemaining() ? 1 : 0;
            witchPoison = room.isWitchPoisonRemaining() ? 1 : 0;
            wolfKill = room.getPendingWolfKillTarget();
        }

        SpeakDirection speakDir = phase == GamePhase.DAY_DISCUSS ? room.getSpeakDirection() : null;
        Integer anchor = phase == GamePhase.DAY_DISCUSS ? room.getSpeakAnchorSeat() : null;
        Integer currentSpeaker = null;
        if (phase == GamePhase.DAY_DISCUSS && !room.getDiscussOrder().isEmpty()) {
            if (room.getDiscussIndex() < room.getDiscussOrder().size()) {
                currentSpeaker = room.getDiscussOrder().get(room.getDiscussIndex());
            }
        }

        String seerAlign = null;
        Integer seerTarget = null;
        if (phase == GamePhase.NIGHT_SEER && viewer != null && viewer.getRole() == Role.SEER) {
            if (room.getLastSeerCheckResult() != null && room.getLastSeerCheckTarget() != null) {
                seerAlign = room.getLastSeerCheckResult() == SeerCheckResult.WOLF ? "WOLF" : "GOOD";
                seerTarget = room.getLastSeerCheckTarget();
            }
        }

        return new PhaseSyncPayload(
                phase,
                room.getRound(),
                defaultCountdown(phase),
                room.alivePlayerIds(),
                yourRole,
                yourTeammates(room, viewer),
                canAct,
                canVote(viewer),
                idiotRev,
                wolfChat,
                witchAntidote,
                witchPoison,
                wolfKill,
                speakDir,
                anchor,
                currentSpeaker,
                seerAlign,
                seerTarget
        );
    }

    private static Integer defaultCountdown(GamePhase phase) {
        return switch (phase) {
            case NIGHT_WOLF -> 30;
            case NIGHT_WITCH -> 30;
            case NIGHT_SEER -> 20;
            case ROLE_ASSIGN -> 5;
            case NIGHT_START, NIGHT_DEATH_ANNOUNCE, EXILE_DEATH_ANNOUNCE, VOTE_RESULT -> 5;
            case DAY_DISCUSS -> 60;
            case DAY_VOTE -> 30;
            case HUNTER_SHOOT -> 20;
            default -> null;
        };
    }

    private static Boolean idiotRevealedForViewer(PlayerState viewer) {
        if (viewer == null || !viewer.isAlive()) {
            return null;
        }
        if (viewer.getRole() == Role.IDIOT) {
            return viewer.isIdiotRevealed();
        }
        return false;
    }

    private static boolean canAct(GameRoomState room, PlayerState viewer, int viewerPlayerId) {
        if (viewer == null) {
            return false;
        }
        if (room.getPhase() == GamePhase.HUNTER_SHOOT
                && room.getHunterShooterSeat() != null
                && room.getHunterShooterSeat() == viewerPlayerId) {
            return true;
        }
        if (!viewer.isAlive()) {
            return false;
        }
        return switch (room.getPhase()) {
            case NIGHT_WOLF -> viewer.getRole() == Role.WEREWOLF;
            case NIGHT_WITCH -> viewer.getRole() == Role.WITCH;
            case NIGHT_SEER -> viewer.getRole() == Role.SEER;
            case HUNTER_SHOOT -> false;
            case DAY_DISCUSS -> {
                List<Integer> order = room.getDiscussOrder();
                int idx = room.getDiscussIndex();
                yield !order.isEmpty() && idx < order.size() && order.get(idx) == viewerPlayerId;
            }
            case DAY_VOTE -> viewer.isCanVote();
            default -> false;
        };
    }

    private static Boolean canVote(PlayerState viewer) {
        if (viewer == null || !viewer.isAlive()) {
            return false;
        }
        return viewer.isCanVote();
    }

    private static List<Integer> yourTeammates(GameRoomState room, PlayerState viewer) {
        if (viewer == null || viewer.getRole() != Role.WEREWOLF) {
            return null;
        }
        List<Integer> teammates = new ArrayList<>();
        for (int wolfId : room.aliveWolfIds()) {
            if (wolfId != viewer.getPlayerId()) {
                teammates.add(wolfId);
            }
        }
        return teammates;
    }
}
