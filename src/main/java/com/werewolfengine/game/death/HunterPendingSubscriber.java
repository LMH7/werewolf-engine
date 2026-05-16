package com.werewolfengine.game.death;

import com.werewolfengine.game.model.GameRoomState;
import com.werewolfengine.game.model.GameWinner;
import com.werewolfengine.game.model.PlayerState;
import com.werewolfengine.game.model.Role;

import java.util.List;
import java.util.Optional;

final class HunterPendingSubscriber implements DeathSubscriber {

    @Override
    public Optional<GameWinner> afterDeaths(GameRoomState room, List<DeathRecord> records) {
        room.setHunterShooterSeat(null);
        room.setPendingHunterAfterAnnounce(resolvePendingHunter(records, room));
        return Optional.empty();
    }

    static Integer resolvePendingHunter(List<DeathRecord> records, GameRoomState room) {
        for (DeathRecord r : records) {
            if (r.cause() != DeathCause.WOLF_KILL) {
                continue;
            }
            PlayerState p = room.getPlayer(r.seat());
            if (p == null || p.getRole() != Role.HUNTER || p.isAlive()) {
                continue;
            }
            if (poisonedSameNight(r.seat(), records)) {
                continue;
            }
            return r.seat();
        }
        return null;
    }

    private static boolean poisonedSameNight(int seat, List<DeathRecord> records) {
        return records.stream()
                .anyMatch(r -> r.seat() == seat && r.cause() == DeathCause.POISON);
    }
}
