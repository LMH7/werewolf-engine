package com.werewolfengine.game.model;

public record GameActionCommand(
        int playerId,
        GameActionType action,
        Integer target,
        GamePhase clientPhase,
        String content
) {
    public GameActionCommand(int playerId, GameActionType action, Integer target, GamePhase clientPhase) {
        this(playerId, action, target, clientPhase, null);
    }
}
