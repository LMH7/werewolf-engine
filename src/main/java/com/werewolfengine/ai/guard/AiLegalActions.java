package com.werewolfengine.ai.guard;

import com.werewolfengine.ai.api.PlayerIntent;
import com.werewolfengine.game.model.GameActionType;
import com.werewolfengine.game.model.GamePhase;
import com.werewolfengine.game.model.GameRoomState;
import com.werewolfengine.game.model.PlayerState;
import com.werewolfengine.game.model.Role;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Validates LLM intent against current phase / seat (PRD §4.5.4 illegal → fallback). */
@Component
public class AiLegalActions {

    public Set<GameActionType> allowed(GameRoomState room, int playerId) {
        PlayerState player = room.getPlayer(playerId);
        if (player == null) {
            return Set.of();
        }
        if (room.getPhase() == GamePhase.HUNTER_SHOOT
                && room.getHunterShooterSeat() != null
                && room.getHunterShooterSeat() == playerId) {
            return EnumSet.of(GameActionType.SHOOT, GameActionType.SKIP);
        }
        List<Integer> lastWords = room.getLastWordsOrder();
        int lwIdx = room.getLastWordsIndex();
        if (room.getPhase() == GamePhase.LAST_WORDS
                && !lastWords.isEmpty()
                && lwIdx < lastWords.size()
                && lastWords.get(lwIdx) == playerId) {
            return EnumSet.of(GameActionType.SPEAK, GameActionType.SKIP_SPEAK);
        }
        if (!player.isAlive()) {
            return Set.of();
        }
        return switch (room.getPhase()) {
            case NIGHT_WOLF -> player.getRole() == Role.WEREWOLF && !room.getWolfKillVotes().containsKey(playerId)
                    ? EnumSet.of(GameActionType.KILL, GameActionType.WOLF_CHAT)
                    : Set.of();
            case NIGHT_SEER -> player.getRole() == Role.SEER
                    && playerId == room.seerSeat()
                    && !room.isSeerActedThisNight()
                    ? EnumSet.of(GameActionType.CHECK)
                    : Set.of();
            case NIGHT_WITCH -> player.getRole() == Role.WITCH
                    && playerId == room.witchSeat()
                    && !room.isWitchActedThisNight()
                    ? EnumSet.of(GameActionType.SKIP, GameActionType.SAVE, GameActionType.POISON)
                    : Set.of();
            case DAY_DISCUSS -> {
                List<Integer> order = room.getDiscussOrder();
                int idx = room.getDiscussIndex();
                if (!order.isEmpty() && idx < order.size() && order.get(idx) == playerId) {
                    yield EnumSet.of(GameActionType.SPEAK, GameActionType.SKIP_SPEAK);
                }
                yield Set.of();
            }
            case DAY_VOTE -> player.isCanVote() && !room.getDayVotes().containsKey(playerId)
                    ? EnumSet.of(GameActionType.VOTE, GameActionType.SKIP_VOTE)
                    : Set.of();
            default -> Set.of();
        };
    }

    public boolean isLegal(GameRoomState room, int playerId, PlayerIntent intent) {
        if (intent == null) {
            return false;
        }
        Set<GameActionType> allowed = allowed(room, playerId);
        if (!allowed.contains(intent.action())) {
            return false;
        }
        return switch (intent.action()) {
            case KILL, CHECK, POISON, VOTE -> intent.target() != null
                    && room.getPlayer(intent.target()) != null
                    && room.getPlayer(intent.target()).isAlive();
            case SAVE -> room.getPendingWolfKillTarget() != null;
            case SKIP, SKIP_SPEAK, SKIP_VOTE, WOLF_CHAT, SPEAK, SHOOT -> true;
        };
    }

    public String formatAllowedList(Set<GameActionType> actions) {
        if (actions.isEmpty()) {
            return "(none)";
        }
        StringBuilder sb = new StringBuilder();
        for (GameActionType a : actions) {
            if (!sb.isEmpty()) {
                sb.append(", ");
            }
            sb.append(a.name());
        }
        return sb.toString();
    }
}
