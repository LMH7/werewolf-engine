package com.werewolfengine.ai.api;

import com.werewolfengine.game.model.GameActionType;

/** Structured action intent (PRD §4.5.3) before SM validation. */
public record PlayerIntent(
        GameActionType action,
        Integer target,
        String reason,
        String content,
        String thinking
) {
    public PlayerIntent(GameActionType action, Integer target, String reason) {
        this(action, target, reason, null, null);
    }

    public PlayerIntent(GameActionType action, Integer target, String reason, String content) {
        this(action, target, reason, content, null);
    }
}
