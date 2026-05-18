package com.werewolfengine.game.engine;

import com.werewolfengine.game.model.ActionAck;
import com.werewolfengine.game.model.ActionErrorCode;
import com.werewolfengine.game.model.GameActionCommand;
import com.werewolfengine.game.model.GameActionType;
import com.werewolfengine.game.model.GamePhase;
import com.werewolfengine.game.model.GameRoomState;
import com.werewolfengine.game.model.PlayerState;
import com.werewolfengine.game.model.RoomStatus;
import com.werewolfengine.game.exile.ExileResolver;
import com.werewolfengine.game.hunter.HunterShootFlow;
import com.werewolfengine.game.lastwords.LastWordsFlow;
import com.werewolfengine.game.model.SpeakDirection;
import com.werewolfengine.game.night.NightSkillPipeline;
import com.werewolfengine.game.death.DeathBus;
import com.werewolfengine.game.setup.RoleAssigner;
import com.werewolfengine.game.sync.PhaseSyncBuilder;
import com.werewolfengine.game.win.GameOutcome;
import com.werewolfengine.message.payload.ActionAckPayload;
import com.werewolfengine.message.payload.PhaseSyncPayload;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Server-authoritative game state machine ? night loop (wolf ? seer ? witch ? settle ?
 * {@link GamePhase#NIGHT_DEATH_ANNOUNCE} / {@link GamePhase#EXILE_DEATH_ANNOUNCE} ? optional hunter),
 * day discuss / vote, win check, next night.
 */
@Component
public class GameStateMachine {

    private final ConcurrentHashMap<String, GameRoomState> rooms = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Object> roomLocks = new ConcurrentHashMap<>();
    private final DeathBus deathBus = new DeathBus();
    private final NightSkillPipeline nightPipeline = new NightSkillPipeline(
            new com.werewolfengine.game.night.NightActions(deathBus));
    private final HunterShootFlow hunterFlow = new HunterShootFlow(deathBus);
    private final LastWordsFlow lastWordsFlow = new LastWordsFlow();
    private final ExileResolver exileResolver = new ExileResolver(deathBus);

    public GameRoomState createRoom(String roomId) {
        return rooms.computeIfAbsent(roomId, GameRoomState::new);
    }

    public Optional<GameRoomState> getRoom(String roomId) {
        return Optional.ofNullable(rooms.get(roomId));
    }

    public void markAllReady(String roomId) {
        withRoom(roomId, room -> {
            if (room.getStatus() != RoomStatus.WAITING) {
                throw new IllegalStateException("Room not in WAITING: " + roomId);
            }
            room.getPlayers().values().forEach(p -> p.setReady(true));
            return null;
        });
    }

    public StartGameResult startGame(String roomId) {
        return withRoom(roomId, room -> {
            if (room.getStatus() != RoomStatus.WAITING) {
                return StartGameResult.failure(ActionErrorCode.ROOM_NOT_WAITING,
                        "Room must be WAITING to start");
            }
            if (!allSeatsReady(room)) {
                return StartGameResult.failure(ActionErrorCode.INVALID_ACTION,
                        "All 12 players must be ready");
            }

            room.prepareMatchStart();
            room.setStatus(RoomStatus.PLAYING);
            room.setPhase(GamePhase.ROLE_ASSIGN);
            RoleAssigner.assign(room);

            room.setRound(1);
            room.setPhase(GamePhase.NIGHT_START);
            nightPipeline.enterNightWolf(room);

            return StartGameResult.success(room.getPhase(), nightPipeline.buildWolfPhaseSyncs(room));
        });
    }

    public HandleActionResult handleAction(String roomId, GameActionCommand command) {
        return withRoom(roomId, room -> {
            if (room.getStatus() != RoomStatus.PLAYING) {
                return failAck(ActionErrorCode.INVALID_PHASE, "Room is not playing", room);
            }
            if (command.clientPhase() != null && command.clientPhase() != room.getPhase()) {
                return failAck(ActionErrorCode.INVALID_PHASE, "Phase mismatch", room);
            }

            PlayerState actor = room.getPlayer(command.playerId());
            boolean hunterShootAsDead = room.getPhase() == GamePhase.HUNTER_SHOOT
                    && room.getHunterShooterSeat() != null
                    && command.playerId() == room.getHunterShooterSeat();
            boolean lastWordsTurn = LastWordsFlow.isCurrentSpeaker(room, command.playerId());
            if (!hunterShootAsDead && !lastWordsTurn && (actor == null || !actor.isAlive())) {
                return failAck(ActionErrorCode.INVALID_ACTION, "Player not alive or unknown", room);
            }

            return switch (room.getPhase()) {
                case NIGHT_WOLF, NIGHT_SEER, NIGHT_WITCH -> nightPipeline.handleAction(room, actor, command);
                case HUNTER_SHOOT -> hunterFlow.handleShoot(
                        room, actor, command,
                        () -> enterDayDiscuss(room),
                        r -> continueAfterVoteResolution(r,
                                ActionAck.ok("??????", GamePhase.HUNTER_SHOOT, null)));
                case LAST_WORDS -> lastWordsFlow.handleAction(room, command, () -> {
                    if (room.isLastWordsAfterExile()) {
                        room.setLastWordsAfterExile(false);
                        return hunterFlow.finishExileAfterAnnounce(room,
                                r -> continueAfterVoteResolution(r,
                                        ActionAck.ok("????", GamePhase.LAST_WORDS, null)));
                    }
                    return hunterFlow.finishNightAfterAnnounce(room, () -> enterDayDiscuss(room));
                });
                case DAY_DISCUSS -> handleDayDiscuss(room, actor, command);
                case DAY_VOTE -> handleDayVote(room, actor, command);
                default -> failAck(ActionErrorCode.INVALID_PHASE,
                        "No player actions in phase " + room.getPhase(), room);
            };
        });
    }

    public PhaseSyncPayload buildPhaseSync(String roomId, int viewerPlayerId) {
        return withRoom(roomId, room -> PhaseSyncBuilder.forPlayer(room, viewerPlayerId));
    }

    public ActionAckPayload toPayload(ActionAck ack) {
        return new ActionAckPayload(
                ack.success(),
                ack.message(),
                ack.code(),
                ack.serverPhase(),
                ack.playerSubState()
        );
    }

    /**
     * ?????????{@link #advanceDayAnnounce} ? phase ????
     */
    public HandleActionResult advanceDayAnnounce(String roomId) {
        return withRoom(roomId, room -> {
            if (room.getStatus() != RoomStatus.PLAYING) {
                return failAck(ActionErrorCode.INVALID_PHASE, "Room is not playing", room);
            }
            return switch (room.getPhase()) {
                case NIGHT_DEATH_ANNOUNCE -> advanceNightDeathAnnounce(room);
                case EXILE_DEATH_ANNOUNCE -> advanceExileDeathAnnounce(room);
                default -> failAck(ActionErrorCode.INVALID_PHASE, "?????????", room);
            };
        });
    }

    private HandleActionResult advanceNightDeathAnnounce(GameRoomState room) {
        return hunterFlow.advanceNightDeathAnnounce(room,
                ActionAck.ok("??????", GamePhase.NIGHT_DEATH_ANNOUNCE, null),
                () -> enterDayDiscuss(room));
    }

    private HandleActionResult advanceExileDeathAnnounce(GameRoomState room) {
        ActionAck ack = ActionAck.ok("??????", GamePhase.EXILE_DEATH_ANNOUNCE, null);
        return hunterFlow.advanceExileDeathAnnounce(room, ack, r -> continueAfterVoteResolution(r, ack));
    }

    private void enterDayDiscuss(GameRoomState room) {
        long t = System.currentTimeMillis() / 1000L;
        int anchor = (int) (t % 12) + 1;
        room.setSpeakAnchorSeat(anchor);
        room.setSpeakDirection(ThreadLocalRandom.current().nextBoolean()
                ? SpeakDirection.CLOCKWISE : SpeakDirection.COUNTER_CLOCKWISE);
        List<Integer> order = buildSpeakOrder(room, anchor, room.getSpeakDirection());
        room.getDiscussOrder().clear();
        room.getDiscussOrder().addAll(order);
        room.setDiscussIndex(0);
        room.setPhase(GamePhase.DAY_DISCUSS);
    }

    private static List<Integer> buildSpeakOrder(GameRoomState room, int anchor, SpeakDirection dir) {
        int first = anchor;
        for (int i = 0; i < 12; i++) {
            PlayerState p = room.getPlayer(first);
            if (p != null && p.isAlive()) {
                break;
            }
            first = nextSeat(first, dir);
        }
        List<Integer> alive = room.alivePlayerIds();
        if (alive.isEmpty()) {
            return List.of();
        }
        List<Integer> out = new ArrayList<>();
        int cur = first;
        int safety = 0;
        while (out.size() < alive.size() && safety < 24) {
            PlayerState p = room.getPlayer(cur);
            if (p != null && p.isAlive() && !out.contains(cur)) {
                out.add(cur);
            }
            cur = nextSeat(cur, dir);
            safety++;
        }
        return out;
    }

    private static int nextSeat(int seat, SpeakDirection dir) {
        if (dir == SpeakDirection.CLOCKWISE) {
            return seat == 12 ? 1 : seat + 1;
        }
        return seat == 1 ? 12 : seat - 1;
    }

    private HandleActionResult handleDayDiscuss(GameRoomState room, PlayerState actor, GameActionCommand command) {
        List<Integer> order = room.getDiscussOrder();
        int idx = room.getDiscussIndex();
        if (order.isEmpty() || idx >= order.size()) {
            return failAck(ActionErrorCode.INVALID_PHASE, "?????????", room);
        }
        int expected = order.get(idx);
        if (actor.getPlayerId() != expected) {
            return failAck(ActionErrorCode.NOT_YOUR_TURN, "???? " + expected + " ???", room);
        }
        return switch (command.action()) {
            case SPEAK, SKIP_SPEAK -> {
                room.setDiscussIndex(idx + 1);
                ActionAck ack = ActionAck.ok("?????", room.getPhase(), null);
                if (room.getDiscussIndex() >= order.size()) {
                    room.getDayVotes().clear();
                    room.setPhase(GamePhase.DAY_VOTE);
                }
                yield HandleActionResult.of(ack, GameOutcome.syncsAllAlive(room));
            }
            default -> failAck(ActionErrorCode.INVALID_ACTION, "??????? SPEAK / SKIP_SPEAK", room);
        };
    }

    private HandleActionResult handleDayVote(GameRoomState room, PlayerState actor, GameActionCommand command) {
        if (!actor.isCanVote()) {
            return failAck(ActionErrorCode.INVALID_ACTION, "?????????", room);
        }
        return switch (command.action()) {
            case VOTE, SKIP_VOTE -> {
                Integer target = command.action() == GameActionType.SKIP_VOTE ? null : command.target();
                room.getDayVotes().put(actor.getPlayerId(), target);
                ActionAck ack = ActionAck.ok("?????", room.getPhase(), null);
                if (allCanVotePlayersSubmitted(room)) {
                    yield resolveDayVote(room, ack);
                }
                yield HandleActionResult.of(ack, GameOutcome.syncsAllAlive(room));
            }
            default -> failAck(ActionErrorCode.INVALID_ACTION, "??????? VOTE / SKIP_VOTE", room);
        };
    }

    private static boolean allCanVotePlayersSubmitted(GameRoomState room) {
        for (PlayerState p : room.getPlayers().values()) {
            if (p.isAlive() && p.isCanVote() && !room.getDayVotes().containsKey(p.getPlayerId())) {
                return false;
            }
        }
        return true;
    }

    private HandleActionResult resolveDayVote(GameRoomState room, ActionAck priorAck) {
        Map<Integer, Integer> votes = new HashMap<>(room.getDayVotes());
        Map<Integer, Integer> counts = new HashMap<>();
        for (Map.Entry<Integer, Integer> e : votes.entrySet()) {
            Integer t = e.getValue();
            if (t != null) {
                counts.merge(t, 1, Integer::sum);
            }
        }
        if (counts.isEmpty()) {
            return advanceAfterVote(room, priorAck, null);
        }
        int max = Collections.max(counts.values());
        List<Integer> tops = new ArrayList<>();
        for (Map.Entry<Integer, Integer> e : counts.entrySet()) {
            if (e.getValue() == max) {
                tops.add(e.getKey());
            }
        }
        if (tops.size() != 1) {
            return advanceAfterVote(room, priorAck, null);
        }
        int exile = tops.getFirst();
        return advanceAfterVote(room, priorAck, exile);
    }

    private HandleActionResult advanceAfterVote(GameRoomState room, ActionAck priorAck, Integer exileSeat) {
        return exileResolver.advanceAfterVote(room, priorAck, exileSeat, r -> continueAfterVoteResolution(r, priorAck));
    }

    private HandleActionResult continueAfterVoteResolution(GameRoomState room, ActionAck priorAck) {
        room.setPhase(GamePhase.CHECK_WIN);
        HandleActionResult ended = GameOutcome.tryEndGame(room, priorAck);
        if (ended != null) {
            return ended;
        }

        room.setRound(room.getRound() + 1);
        room.setPhase(GamePhase.NIGHT_START);
        nightPipeline.enterNightWolf(room);
        return HandleActionResult.of(priorAck, GameOutcome.syncsAllAlive(room));
    }

    private static HandleActionResult failAck(ActionErrorCode code, String msg, GameRoomState room) {
        return HandleActionResult.of(ActionAck.fail(code, msg, room.getPhase()), List.of());
    }

    private static boolean allSeatsReady(GameRoomState room) {
        return room.getPlayers().values().stream().allMatch(PlayerState::isReady);
    }

    private <T> T withRoom(String roomId, RoomOperation<T> operation) {
        Object lock = roomLocks.computeIfAbsent(roomId, id -> new Object());
        synchronized (lock) {
            GameRoomState room = rooms.computeIfAbsent(roomId, GameRoomState::new);
            return operation.apply(room);
        }
    }

    @FunctionalInterface
    private interface RoomOperation<T> {
        T apply(GameRoomState room);
    }

    public record StartGameResult(
            boolean success,
            ActionErrorCode errorCode,
            String message,
            GamePhase phase,
            List<PhaseSyncPayload> phaseSyncs
    ) {
        static StartGameResult success(GamePhase phase, List<PhaseSyncPayload> syncs) {
            return new StartGameResult(true, null, null, phase, syncs);
        }

        static StartGameResult failure(ActionErrorCode code, String message) {
            return new StartGameResult(false, code, message, null, List.of());
        }
    }

    public record HandleActionResult(ActionAck ack, List<PhaseSyncPayload> phaseSyncs) {
        public static HandleActionResult of(ActionAck ack, List<PhaseSyncPayload> syncs) {
            return new HandleActionResult(ack, syncs);
        }
    }
}
