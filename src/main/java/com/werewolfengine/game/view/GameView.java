package com.werewolfengine.game.view;

import com.werewolfengine.game.model.GamePhase;
import com.werewolfengine.game.model.Role;

import java.util.List;

/**
 * Per-seat read-only game snapshot for AI prompts and PHASE_SYNC alignment (ADR-003 §5.1).
 */
public record GameView(
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
        Integer currentSpeaker,
        boolean canAct
) {
}
