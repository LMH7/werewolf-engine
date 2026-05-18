package com.werewolfengine.game.view;

import com.werewolfengine.game.model.GamePhase;

/**
 * Neutral per-seat visible fact from action_log (no Prompt text).
 */
public record VisibleEvent(
        int round,
        GamePhase phase,
        PerceptionEventKind kind,
        Integer actorSeat,
        Integer targetSeat,
        String summary
) {
}
