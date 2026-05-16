package com.werewolfengine.game.model;

/** Seer sees GOOD (村民/神) vs WOLF only — PRD R12. */
public enum SeerCheckResult {
    GOOD,
    WOLF;

    public static SeerCheckResult forRole(Role role) {
        if (role == null) {
            return GOOD;
        }
        return role == Role.WEREWOLF ? WOLF : GOOD;
    }
}
