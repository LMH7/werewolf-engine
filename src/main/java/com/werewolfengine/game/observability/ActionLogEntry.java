package com.werewolfengine.game.observability;

import com.werewolfengine.game.model.GameActionType;
import com.werewolfengine.game.model.GamePhase;
import com.werewolfengine.game.model.Role;

/**
 * PRD §4.7.3 — one structured action line (in-memory MVP).
 */
public record ActionLogEntry(
        String roomId,
        int round,
        GamePhase phase,
        int playerId,
        Role role,
        GameActionType action,
        Integer target,
        String content,
        boolean success,
        long timestamp,
        String thinking,
        String modelId
) {
}
