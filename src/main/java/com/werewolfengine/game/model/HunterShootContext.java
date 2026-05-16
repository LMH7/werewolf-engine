package com.werewolfengine.game.model;

/** Origin of the current {@link GamePhase#HUNTER_SHOOT} — determines post-shoot flow. */
public enum HunterShootContext {
    /** After night deaths + announce; then {@link GamePhase#DAY_DISCUSS}. */
    NIGHT_DEATHS,
    /** After vote exile + announce; then {@link GamePhase#CHECK_WIN} / next night. */
    DAY_EXILE
}
