package com.werewolfengine.game.night;

import com.werewolfengine.game.death.DeathBus;
import com.werewolfengine.game.death.DeathCause;
import com.werewolfengine.game.death.DeathRecord;
import com.werewolfengine.game.death.DeathApplyResult;
import com.werewolfengine.game.model.GameRoomState;
import com.werewolfengine.game.model.PlayerState;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * PRD §3.4 — wolf kill (respect save), poison; deaths via {@link DeathBus}.
 */
public final class NightResolver {

    private NightResolver() {
    }

    /**
     * @return true if R23 ended the game
     */
    public static boolean applyNightDeaths(GameRoomState room, DeathBus deathBus) {
        Integer wolfTarget = room.getPendingWolfKillTarget();
        boolean saveUsed = Boolean.TRUE.equals(room.getWitchUsedSaveTonight());
        Integer poisonTarget = room.getWitchPoisonTargetTonight();

        boolean wolfStrike = wolfTarget != null
                && room.getPlayer(wolfTarget) != null
                && room.getPlayer(wolfTarget).isAlive();

        boolean consumedAntidote = false;
        if (wolfStrike && saveUsed && room.isWitchAntidoteRemaining()) {
            wolfStrike = false;
            consumedAntidote = true;
        }

        Set<Integer> toKill = new LinkedHashSet<>();
        if (wolfStrike) {
            toKill.add(wolfTarget);
        }
        boolean poisonApplied = false;
        if (poisonTarget != null) {
            PlayerState p = room.getPlayer(poisonTarget);
            if (p != null && p.isAlive()) {
                toKill.add(poisonTarget);
                poisonApplied = true;
            }
        }

        if (consumedAntidote) {
            room.setWitchAntidoteRemaining(false);
        }
        if (poisonApplied) {
            room.setWitchPoisonRemaining(false);
        }

        List<DeathRecord> records = new ArrayList<>();
        if (wolfStrike) {
            records.add(new DeathRecord(wolfTarget, DeathCause.WOLF_KILL));
        }
        if (poisonApplied) {
            records.add(new DeathRecord(poisonTarget, DeathCause.POISON));
        }

        List<Integer> deaths = new ArrayList<>(toKill);
        deaths.sort(Integer::compareTo);
        room.setLastNightDeaths(deaths);
        room.clearNightIntent();

        DeathApplyResult result = deathBus.apply(room, records);
        return result.gameEnded();
    }
}
