package com.werewolfengine.message.payload;

import com.werewolfengine.game.model.GamePhase;
import com.werewolfengine.game.model.Role;
import com.werewolfengine.game.model.SpeakDirection;

import java.util.List;

/** PRD §4.6.4 — per-recipient phase snapshot. */
public record PhaseSyncPayload(
        GamePhase currentPhase,
        int round,
        Integer countdown,
        List<Integer> alivePlayers,
        Role yourRole,
        List<Integer> yourTeammates,
        Boolean canAct,
        Boolean canVote,
        Boolean idiotRevealed,
        Boolean wolfChatInPhase,
        Integer witchAntidoteLeft,
        Integer witchPoisonLeft,
        Integer wolfKillTarget,
        SpeakDirection speakDirection,
        Integer speakAnchorSeat,
        Integer currentSpeakerId,
        String seerCheckAlignment,
        Integer seerCheckTarget
) {
}
