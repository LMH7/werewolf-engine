package com.werewolfengine.game.night;

import com.werewolfengine.game.model.GameRoomState;

import java.util.Map;

/** Callback when all wolves voted and {@link WolfVoteResolver} picked the night kill target. */
@FunctionalInterface
public interface WolfKillResolvedListener {

    void onResolved(GameRoomState room, int targetSeat, Map<Integer, Integer> wolfVotesBySeat);
}
