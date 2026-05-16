package com.werewolfengine.game.death;

import com.werewolfengine.game.model.GameRoomState;
import com.werewolfengine.game.model.GameWinner;

import java.util.List;
import java.util.Optional;

@FunctionalInterface
interface DeathSubscriber {

    Optional<GameWinner> afterDeaths(GameRoomState room, List<DeathRecord> records);
}
