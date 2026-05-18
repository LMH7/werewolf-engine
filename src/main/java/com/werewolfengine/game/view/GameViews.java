package com.werewolfengine.game.view;

import com.werewolfengine.game.model.GamePhase;
import com.werewolfengine.game.model.GameRoomState;
import com.werewolfengine.game.model.PlayerState;
import com.werewolfengine.game.model.Role;
import com.werewolfengine.game.model.SeerCheckResult;

import java.util.ArrayList;
import java.util.List;

/** Builds {@link GameView} with the same visibility rules as {@link com.werewolfengine.game.sync.PhaseSyncBuilder}. */
public final class GameViews {

    private GameViews() {
    }

    public static GameView forSeat(GameRoomState room, int playerId) {
        PlayerState self = room.getPlayer(playerId);
        Role role = self != null ? self.getRole() : null;
        GamePhase phase = room.getPhase();

        List<Integer> teammates = null;
        if (role == Role.WEREWOLF) {
            teammates = new ArrayList<>();
            for (int w : room.aliveWolfIds()) {
                if (w != playerId) {
                    teammates.add(w);
                }
            }
        }

        Integer wolfKill = null;
        boolean antidote = false;
        boolean poison = false;
        if (role == Role.WITCH && phase == GamePhase.NIGHT_WITCH) {
            wolfKill = room.getPendingWolfKillTarget();
            antidote = room.isWitchAntidoteRemaining();
            poison = room.isWitchPoisonRemaining();
        }

        String seerResult = null;
        Integer seerTarget = null;
        if (role == Role.SEER && room.getLastSeerCheckResult() != null) {
            seerResult = room.getLastSeerCheckResult().name();
            seerTarget = room.getLastSeerCheckTarget();
        }

        Integer speaker = currentSpeaker(room);

        return new GameView(
                playerId,
                role,
                phase,
                room.getRound(),
                List.copyOf(room.alivePlayerIds()),
                teammates,
                wolfKill,
                antidote,
                poison,
                seerResult,
                seerTarget,
                room.isWolfChatInPhase(),
                speaker,
                SeatVisibility.canAct(room, playerId)
        );
    }

    public static boolean canAct(GameRoomState room, int viewerPlayerId) {
        return SeatVisibility.canAct(room, viewerPlayerId);
    }

    private static Integer currentSpeaker(GameRoomState room) {
        if (room.getPhase() == GamePhase.DAY_DISCUSS
                && room.getDiscussIndex() < room.getDiscussOrder().size()) {
            return room.getDiscussOrder().get(room.getDiscussIndex());
        }
        if (room.getPhase() == GamePhase.LAST_WORDS
                && room.getLastWordsIndex() < room.getLastWordsOrder().size()) {
            return room.getLastWordsOrder().get(room.getLastWordsIndex());
        }
        return null;
    }

    /** Seer alignment label for PHASE_SYNC (GOOD/WOLF), null if hidden. */
    public static String seerAlignmentForViewer(GameRoomState room, PlayerState viewer) {
        if (room.getPhase() != GamePhase.NIGHT_SEER || viewer == null || viewer.getRole() != Role.SEER) {
            return null;
        }
        if (room.getLastSeerCheckResult() == null || room.getLastSeerCheckTarget() == null) {
            return null;
        }
        return room.getLastSeerCheckResult() == SeerCheckResult.WOLF ? "WOLF" : "GOOD";
    }
}
