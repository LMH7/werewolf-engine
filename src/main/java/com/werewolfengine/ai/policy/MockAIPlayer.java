package com.werewolfengine.ai.policy;

import com.werewolfengine.ai.api.PlayerIntent;
import com.werewolfengine.game.model.GameActionType;
import com.werewolfengine.game.model.GamePhase;
import com.werewolfengine.game.model.GameRoomState;
import com.werewolfengine.game.model.PlayerState;
import com.werewolfengine.game.model.Role;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * Week1 Mock AI — no LLM. Per PRD §4.5.4 fallback: random kill / check / vote, first-night save, skips elsewhere.
 */
@Component
public class MockAIPlayer {

    public Optional<PlayerIntent> decide(GameRoomState room, int playerId) {
        PlayerState player = room.getPlayer(playerId);
        if (player == null) {
            return Optional.empty();
        }

        return switch (room.getPhase()) {
            case NIGHT_WOLF -> decideNightWolf(room, player);
            case NIGHT_SEER -> decideNightSeer(room, player);
            case NIGHT_WITCH -> decideNightWitch(room, player);
            case DAY_DISCUSS -> decideDayDiscuss(room, playerId);
            case DAY_VOTE -> decideDayVote(room, player);
            case HUNTER_SHOOT -> decideHunterShoot(room, playerId);
            case LAST_WORDS -> decideLastWords(room, playerId);
            default -> Optional.empty();
        };
    }

    private Optional<PlayerIntent> decideNightWolf(GameRoomState room, PlayerState player) {
        if (!player.isAlive() || player.getRole() != Role.WEREWOLF) {
            return Optional.empty();
        }
        if (room.getWolfKillVotes().containsKey(player.getPlayerId())) {
            return Optional.empty();
        }
        if (!room.isWolfChatInPhase() && shouldWolfChatBeforeKill(room, player.getPlayerId())) {
            return Optional.of(new PlayerIntent(
                    GameActionType.WOLF_CHAT,
                    null,
                    "mock wolf chat",
                    "商议今晚刀口"
            ));
        }
        return Optional.of(randomKillNonWolf(room, player.getPlayerId()));
    }

    private static boolean shouldWolfChatBeforeKill(GameRoomState room, int wolfId) {
        if (room.isWolfChatInPhase()) {
            return false;
        }
        int firstWolf = room.aliveWolfIds().stream().sorted().findFirst().orElse(-1);
        return wolfId == firstWolf;
    }

    private Optional<PlayerIntent> decideNightSeer(GameRoomState room, PlayerState player) {
        if (!player.isAlive() || player.getRole() != Role.SEER || room.isSeerActedThisNight()) {
            return Optional.empty();
        }
        if (player.getPlayerId() != room.seerSeat()) {
            return Optional.empty();
        }
        List<Integer> targets = room.alivePlayerIds().stream()
                .filter(id -> id != player.getPlayerId())
                .collect(Collectors.toList());
        if (targets.isEmpty()) {
            return Optional.empty();
        }
        int target = targets.get(ThreadLocalRandom.current().nextInt(targets.size()));
        return Optional.of(new PlayerIntent(GameActionType.CHECK, target, "mock seer check"));
    }

    private Optional<PlayerIntent> decideNightWitch(GameRoomState room, PlayerState player) {
        if (!player.isAlive() || player.getRole() != Role.WITCH || room.isWitchActedThisNight()) {
            return Optional.empty();
        }
        if (player.getPlayerId() != room.witchSeat()) {
            return Optional.empty();
        }
        if (room.getRound() == 1
                && room.getPendingWolfKillTarget() != null
                && room.isWitchAntidoteRemaining()) {
            return Optional.of(new PlayerIntent(GameActionType.SAVE, null, "mock first-night save"));
        }
        return Optional.of(new PlayerIntent(GameActionType.SKIP, null, "mock witch skip"));
    }

    private Optional<PlayerIntent> decideDayDiscuss(GameRoomState room, int playerId) {
        List<Integer> order = room.getDiscussOrder();
        int idx = room.getDiscussIndex();
        if (order.isEmpty() || idx >= order.size() || order.get(idx) != playerId) {
            return Optional.empty();
        }
        PlayerState p = room.getPlayer(playerId);
        if (p == null || !p.isAlive()) {
            return Optional.empty();
        }
        if (ThreadLocalRandom.current().nextInt(4) == 0) {
            return Optional.of(new PlayerIntent(
                    GameActionType.SPEAK,
                    null,
                    "mock speak",
                    "我是" + playerId + "号，过。"
            ));
        }
        return Optional.of(new PlayerIntent(GameActionType.SKIP_SPEAK, null, "mock skip speak"));
    }

    private Optional<PlayerIntent> decideDayVote(GameRoomState room, PlayerState player) {
        if (!player.isAlive() || !player.isCanVote() || room.getDayVotes().containsKey(player.getPlayerId())) {
            return Optional.empty();
        }
        List<Integer> targets = room.alivePlayerIds().stream()
                .filter(id -> id != player.getPlayerId())
                .collect(Collectors.toList());
        if (targets.isEmpty()) {
            return Optional.of(new PlayerIntent(GameActionType.SKIP_VOTE, null, "mock skip vote"));
        }
        int target = targets.get(ThreadLocalRandom.current().nextInt(targets.size()));
        return Optional.of(new PlayerIntent(GameActionType.VOTE, target, "mock random vote"));
    }

    private Optional<PlayerIntent> decideHunterShoot(GameRoomState room, int playerId) {
        Integer shooter = room.getHunterShooterSeat();
        if (shooter == null || shooter != playerId) {
            return Optional.empty();
        }
        return Optional.of(new PlayerIntent(GameActionType.SKIP, null, "mock hunter skip"));
    }

    private Optional<PlayerIntent> decideLastWords(GameRoomState room, int playerId) {
        List<Integer> order = room.getLastWordsOrder();
        int idx = room.getLastWordsIndex();
        if (order.isEmpty() || idx >= order.size() || order.get(idx) != playerId) {
            return Optional.empty();
        }
        return Optional.of(new PlayerIntent(GameActionType.SKIP_SPEAK, null, "mock skip last words"));
    }

    private PlayerIntent randomKillNonWolf(GameRoomState room, int wolfId) {
        List<Integer> targets = room.alivePlayerIds().stream()
                .filter(id -> {
                    PlayerState p = room.getPlayer(id);
                    return p != null && p.getRole() != Role.WEREWOLF;
                })
                .collect(Collectors.toList());
        if (targets.isEmpty()) {
            return new PlayerIntent(GameActionType.SKIP, null, "no non-wolf target");
        }
        int target = targets.get(ThreadLocalRandom.current().nextInt(targets.size()));
        return new PlayerIntent(GameActionType.KILL, target, "mock random kill");
    }
}
