package com.werewolfengine.game;

import com.werewolfengine.game.model.GameRoomState;
import com.werewolfengine.game.model.PlayerState;
import com.werewolfengine.game.model.Role;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * PRD R10 — majority vote among wolves; tie → last chronologically submitted vote among tied
 * targets; still tied → random alive non-wolf. No votes → random alive non-wolf.
 */
public final class WolfVoteResolver {

    private WolfVoteResolver() {
    }

    public static int resolveKillTarget(GameRoomState room) {
        List<Integer> wolves = room.aliveWolfIds();
        if (wolves.isEmpty()) {
            return randomNonWolf(room);
        }
        Map<Integer, Integer> votes = room.getWolfKillVotes();
        if (votes.isEmpty()) {
            return randomNonWolf(room);
        }

        Map<Integer, Long> counts = new HashMap<>();
        for (int w : wolves) {
            Integer t = votes.get(w);
            if (t != null) {
                counts.merge(t, 1L, Long::sum);
            }
        }
        if (counts.isEmpty()) {
            return randomNonWolf(room);
        }

        long max = counts.values().stream().max(Long::compare).orElse(0L);
        List<Integer> topTargets = counts.entrySet().stream()
                .filter(e -> e.getValue() == max)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        if (topTargets.size() == 1) {
            return topTargets.getFirst();
        }

        List<GameRoomState.WolfKillEvent> log = room.getWolfKillEventLog();
        for (int i = log.size() - 1; i >= 0; i--) {
            int target = log.get(i).targetId();
            if (topTargets.contains(target)) {
                return target;
            }
        }

        List<Integer> nonWolves = topTargets.stream()
                .filter(t -> {
                    PlayerState p = room.getPlayer(t);
                    return p != null && p.isAlive() && p.getRole() != Role.WEREWOLF;
                })
                .collect(Collectors.toList());
        if (!nonWolves.isEmpty()) {
            return nonWolves.get(ThreadLocalRandom.current().nextInt(nonWolves.size()));
        }
        return topTargets.get(ThreadLocalRandom.current().nextInt(topTargets.size()));
    }

    private static int randomNonWolf(GameRoomState room) {
        List<Integer> candidates = room.alivePlayerIds().stream()
                .filter(id -> {
                    PlayerState p = room.getPlayer(id);
                    return p != null && p.getRole() != Role.WEREWOLF;
                })
                .sorted()
                .collect(Collectors.toCollection(ArrayList::new));
        if (candidates.isEmpty()) {
            List<Integer> any = new ArrayList<>(room.alivePlayerIds());
            Collections.shuffle(any);
            return any.isEmpty() ? 1 : any.getFirst();
        }
        return candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
    }
}
