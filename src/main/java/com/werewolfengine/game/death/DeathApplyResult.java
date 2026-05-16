package com.werewolfengine.game.death;

import com.werewolfengine.game.model.GameWinner;

import java.util.Optional;

public record DeathApplyResult(boolean gameEnded, Optional<GameWinner> winner) {

    public static DeathApplyResult continued() {
        return new DeathApplyResult(false, Optional.empty());
    }

    public static DeathApplyResult ended(GameWinner winner) {
        return new DeathApplyResult(true, Optional.of(winner));
    }
}
