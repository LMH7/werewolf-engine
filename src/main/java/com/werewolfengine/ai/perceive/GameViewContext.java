package com.werewolfengine.ai.perceive;

import com.werewolfengine.game.model.GamePhase;
import com.werewolfengine.game.model.GameRoomState;
import com.werewolfengine.game.model.Role;
import com.werewolfengine.game.view.GameView;
import com.werewolfengine.game.view.GameViews;

import java.util.List;

/** Prompt adapter over {@link GameView} (ADR-003 §5.1). */
public record GameViewContext(
        int seat,
        Role yourRole,
        GamePhase phase,
        int round,
        List<Integer> aliveSeats,
        List<Integer> wolfTeammates,
        Integer pendingWolfKill,
        boolean witchAntidoteLeft,
        boolean witchPoisonLeft,
        String lastSeerResult,
        Integer lastSeerTarget,
        boolean wolfChatDone,
        Integer currentSpeaker
) {

    public static GameViewContext forSeat(GameRoomState room, int playerId) {
        return from(GameViews.forSeat(room, playerId));
    }

    public static GameViewContext from(GameView view) {
        return new GameViewContext(
                view.seat(),
                view.yourRole(),
                view.phase(),
                view.round(),
                view.aliveSeats(),
                view.wolfTeammates(),
                view.pendingWolfKill(),
                view.witchAntidoteLeft(),
                view.witchPoisonLeft(),
                view.lastSeerResult(),
                view.lastSeerTarget(),
                view.wolfChatDone(),
                view.currentSpeaker()
        );
    }
}
