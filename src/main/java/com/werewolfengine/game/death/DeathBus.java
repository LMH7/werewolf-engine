package com.werewolfengine.game.death;

import com.werewolfengine.game.model.GameRoomState;
import com.werewolfengine.game.model.PlayerState;
import com.werewolfengine.game.observability.ActionLogService;

import java.util.List;

public final class DeathBus {

    private final List<DeathSubscriber> subscribers;

    public DeathBus() {
        this((ActionLogService) null);
    }

    public DeathBus(ActionLogService actionLog) {
        this(List.of(new HunterPendingSubscriber(), new WinCheckDeathSubscriber(actionLog)));
    }

    DeathBus(List<DeathSubscriber> subscribers) {
        this.subscribers = List.copyOf(subscribers);
    }

    public DeathApplyResult apply(GameRoomState room, List<DeathRecord> records) {
        if (records == null || records.isEmpty()) {
            return DeathApplyResult.continued();
        }
        for (DeathRecord r : records) {
            PlayerState p = room.getPlayer(r.seat());
            if (p != null && p.isAlive()) {
                p.setAlive(false);
            }
        }
        for (DeathSubscriber subscriber : subscribers) {
            var winner = subscriber.afterDeaths(room, records);
            if (winner.isPresent()) {
                return DeathApplyResult.ended(winner.get());
            }
        }
        return DeathApplyResult.continued();
    }
}
