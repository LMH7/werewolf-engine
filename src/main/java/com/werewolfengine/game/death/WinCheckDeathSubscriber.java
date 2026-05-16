package com.werewolfengine.game.death;

import com.werewolfengine.game.GameOutcome;
import com.werewolfengine.game.model.GameRoomState;
import com.werewolfengine.game.model.GameWinner;

import java.util.List;
import java.util.Optional;

final class WinCheckDeathSubscriber implements DeathSubscriber {

    @Override
    public Optional<GameWinner> afterDeaths(GameRoomState room, List<DeathRecord> records) {
        return GameOutcome.evaluateAndEndIfMet(room);
    }
}
