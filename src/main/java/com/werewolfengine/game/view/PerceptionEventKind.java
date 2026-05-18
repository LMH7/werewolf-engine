package com.werewolfengine.game.view;

/** Seat-visible episodic event kinds (ADR-004 §3.3). */
public enum PerceptionEventKind {
    SELF_ACTION,
    SELF_THINKING,
    PUBLIC_SPEAK,
    PUBLIC_VOTE,
    VOTE_RESULT,
    WOLF_CHAT,
    WOLF_KILL_RESOLVED,
    NIGHT_DEATH,
    EXILE_DEATH,
    IDIOT_REVEAL,
    SEER_RESULT,
    WITCH_SELF,
    HUNTER_SHOOT,
    GAME_OVER,
    SYSTEM_OTHER
}
