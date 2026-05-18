package com.werewolfengine.game.orchestration;

import com.werewolfengine.game.model.GamePhase;
import com.werewolfengine.game.model.GameRoomState;
import com.werewolfengine.game.model.PlayerState;
import com.werewolfengine.game.model.Role;
import com.werewolfengine.game.view.GameViews;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Resolves the next seat that should act in the current phase (ADR-003 §4). No random strategy.
 */
@Component
public class TurnActorResolver {

    public Optional<Integer> nextActor(GameRoomState room) {
        return switch (room.getPhase()) {
            case NIGHT_WOLF -> nextWolfWithoutVote(room);
            case NIGHT_SEER -> seerSeatIfPending(room);
            case NIGHT_WITCH -> witchSeatIfPending(room);
            case DAY_DISCUSS -> currentDiscussSpeaker(room);
            case DAY_VOTE -> nextVoterWithoutBallot(room);
            case HUNTER_SHOOT -> Optional.ofNullable(room.getHunterShooterSeat());
            case LAST_WORDS -> currentLastWordsSpeaker(room);
            default -> Optional.empty();
        };
    }

    /**
     * Server-driven AI may act for this seat (ADR-003 §10 #7: {@code userId == null}).
     */
    public boolean isServerAiSeat(GameRoomState room, int playerId) {
        PlayerState p = room.getPlayer(playerId);
        return p != null && p.getHumanUserId() == null;
    }

    public Optional<Integer> nextAiActor(GameRoomState room) {
        Optional<Integer> actor = nextActor(room);
        if (actor.isEmpty()) {
            return Optional.empty();
        }
        int seat = actor.get();
        if (!isServerAiSeat(room, seat)) {
            return Optional.empty();
        }
        if (!GameViews.canAct(room, seat) && room.getPhase() != GamePhase.HUNTER_SHOOT
                && room.getPhase() != GamePhase.LAST_WORDS) {
            return Optional.empty();
        }
        return actor;
    }

    private static Optional<Integer> nextWolfWithoutVote(GameRoomState room) {
        for (int wolfId : room.aliveWolfIds()) {
            if (!room.getWolfKillVotes().containsKey(wolfId)) {
                return Optional.of(wolfId);
            }
        }
        return Optional.empty();
    }

    private static Optional<Integer> seerSeatIfPending(GameRoomState room) {
        int ss = room.seerSeat();
        if (ss <= 0 || room.isSeerActedThisNight()) {
            return Optional.empty();
        }
        PlayerState s = room.getPlayer(ss);
        if (s == null || !s.isAlive() || s.getRole() != Role.SEER) {
            return Optional.empty();
        }
        return Optional.of(ss);
    }

    private static Optional<Integer> witchSeatIfPending(GameRoomState room) {
        int ws = room.witchSeat();
        if (ws <= 0 || room.isWitchActedThisNight()) {
            return Optional.empty();
        }
        PlayerState w = room.getPlayer(ws);
        if (w == null || !w.isAlive() || w.getRole() != Role.WITCH) {
            return Optional.empty();
        }
        return Optional.of(ws);
    }

    private static Optional<Integer> currentDiscussSpeaker(GameRoomState room) {
        List<Integer> order = room.getDiscussOrder();
        int idx = room.getDiscussIndex();
        if (order.isEmpty() || idx >= order.size()) {
            return Optional.empty();
        }
        return Optional.of(order.get(idx));
    }

    private static Optional<Integer> nextVoterWithoutBallot(GameRoomState room) {
        List<Integer> pending = new ArrayList<>();
        for (PlayerState p : room.getPlayers().values()) {
            if (p.isAlive() && p.isCanVote() && !room.getDayVotes().containsKey(p.getPlayerId())) {
                pending.add(p.getPlayerId());
            }
        }
        if (pending.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(pending.getFirst());
    }

    private static Optional<Integer> currentLastWordsSpeaker(GameRoomState room) {
        List<Integer> order = room.getLastWordsOrder();
        int idx = room.getLastWordsIndex();
        if (order.isEmpty() || idx >= order.size()) {
            return Optional.empty();
        }
        return Optional.of(order.get(idx));
    }
}
