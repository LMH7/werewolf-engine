package com.werewolfengine.game.model;

/** Why {@link GamePhase#DAY_ANNOUNCE} was entered — drives the next transition after announce. */
public enum DayAnnounceSource {
    /** Night deaths applied; next is optional hunter then {@link GamePhase#DAY_DISCUSS}. */
    NIGHT_SETTLEMENT,
    /** Hunter was voted out; next is optional {@link GamePhase#HUNTER_SHOOT} then vote tail. */
    DAY_VOTE_EXILE
}
