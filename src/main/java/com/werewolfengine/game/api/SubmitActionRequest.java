package com.werewolfengine.game.api;

import com.werewolfengine.game.model.GameActionType;
import com.werewolfengine.game.model.GamePhase;

public record SubmitActionRequest(
        int playerId,
        GameActionType action,
        Integer target,
        GamePhase phase,
        String content
) {
}
