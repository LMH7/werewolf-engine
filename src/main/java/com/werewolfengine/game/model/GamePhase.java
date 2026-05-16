package com.werewolfengine.game.model;

/**
 * PRD §4.3.1 — server-authoritative phase enum (frozen).
 */
public enum GamePhase {
    WAITING,
    ROLE_ASSIGN,
    NIGHT_START,
    NIGHT_WOLF,
    NIGHT_SEER,
    NIGHT_WITCH,
    HUNTER_SHOOT,
    DAY_ANNOUNCE,
    DAY_DISCUSS,
    DAY_VOTE,
    VOTE_RESULT,
    CHECK_WIN,
    GAME_OVER
}
