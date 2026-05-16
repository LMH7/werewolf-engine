package com.werewolfengine.game;

import com.werewolfengine.game.model.ActionAck;
import com.werewolfengine.game.model.ActionErrorCode;
import com.werewolfengine.game.model.GameActionCommand;
import com.werewolfengine.game.model.GameActionType;
import com.werewolfengine.game.model.GamePhase;
import com.werewolfengine.game.model.GameRoomState;
import com.werewolfengine.game.model.GameWinner;
import com.werewolfengine.game.model.PlayerState;
import com.werewolfengine.game.model.Role;
import com.werewolfengine.game.model.RoomStatus;
import com.werewolfengine.game.model.SeerCheckResult;
import com.werewolfengine.game.model.SpeakDirection;
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
 * Server-authoritative game state machine — night loop (wolf → witch → seer → settle),
 * hunter shoot, day discuss / vote, win check, next night.
 */
@Component
public class GameStateMachine {

    private final ConcurrentHashMap<String, GameRoomState> rooms = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Object> roomLocks = new ConcurrentHashMap<>();

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
            enterNightWolf(room);

            return StartGameResult.success(room.getPhase(), buildWolfPhaseSyncs(room));
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
            if (!hunterShootAsDead && (actor == null || !actor.isAlive())) {
                return failAck(ActionErrorCode.INVALID_ACTION, "Player not alive or unknown", room);
            }

            return switch (room.getPhase()) {
                case NIGHT_WOLF -> handleNightWolf(room, actor, command);
                case NIGHT_WITCH -> handleNightWitch(room, actor, command);
                case NIGHT_SEER -> handleNightSeer(room, actor, command);
                case HUNTER_SHOOT -> handleHunterShoot(room, actor, command);
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

    private HandleActionResult handleNightWolf(GameRoomState room, PlayerState actor, GameActionCommand command) {
        return switch (command.action()) {
            case KILL -> handleKill(room, actor, command.target());
            case WOLF_CHAT -> handleWolfChat(room, actor);
            default -> failAck(ActionErrorCode.INVALID_ACTION,
                    "Action not allowed in NIGHT_WOLF: " + command.action(), room);
        };
    }

    private HandleActionResult handleKill(GameRoomState room, PlayerState actor, Integer targetId) {
        if (actor.getRole() != Role.WEREWOLF) {
            return failAck(ActionErrorCode.INVALID_ACTION, "Only wolves can KILL", room);
        }
        if (targetId == null) {
            return failAck(ActionErrorCode.INVALID_TARGET, "KILL requires target", room);
        }
        PlayerState target = room.getPlayer(targetId);
        if (target == null || !target.isAlive()) {
            return failAck(ActionErrorCode.INVALID_TARGET, "Target must be alive", room);
        }
        if (target.getRole() == Role.WEREWOLF && !room.isWolfChatInPhase()) {
            return failAck(ActionErrorCode.WOLF_CHAT_REQUIRED,
                    "刀狼队友或自刀前，须在本夜晚狼人阶段先进行狼队频道商议", room);
        }

        room.getWolfKillVotes().put(actor.getPlayerId(), targetId);
        room.appendWolfKillEvent(actor.getPlayerId(), targetId);

        ActionAck ack = ActionAck.ok("刀人目标已记录", room.getPhase(), "WAITING_WOLF_CONSENSUS");
        Optional<HandleActionResult> transition = tryAdvanceAfterAllWolvesVoted(room);
        return transition.orElseGet(() -> HandleActionResult.of(ack, List.of()));
    }

    private HandleActionResult handleWolfChat(GameRoomState room, PlayerState actor) {
        if (actor.getRole() != Role.WEREWOLF) {
            return failAck(ActionErrorCode.INVALID_ACTION, "Only wolves can WOLF_CHAT", room);
        }
        room.setWolfChatInPhase(true);
        ActionAck ack = ActionAck.ok("狼队商议已记录", room.getPhase(), null);
        return HandleActionResult.of(ack, buildWolfPhaseSyncs(room));
    }

    private Optional<HandleActionResult> tryAdvanceAfterAllWolvesVoted(GameRoomState room) {
        List<Integer> wolves = room.aliveWolfIds();
        if (wolves.isEmpty()) {
            return Optional.empty();
        }
        for (int w : wolves) {
            if (!room.getWolfKillVotes().containsKey(w)) {
                return Optional.empty();
            }
        }

        int resolved = WolfVoteResolver.resolveKillTarget(room);
        room.setPendingWolfKillTarget(resolved);
        room.clearWolfVotesAndLog();

        ActionAck ack = ActionAck.ok("狼人阶段结束，刀口已结算", GamePhase.NIGHT_WITCH, null);
        return Optional.of(runAutopilotNightPhases(room, enterNightWitch(room, ack)));
    }

    private static boolean witchCanAct(GameRoomState room) {
        int ws = room.witchSeat();
        if (ws <= 0) {
            return false;
        }
        PlayerState w = room.getPlayer(ws);
        return w != null && w.isAlive() && w.getRole() == Role.WITCH;
    }

    private static boolean seerCanAct(GameRoomState room) {
        int ss = room.seerSeat();
        if (ss <= 0) {
            return false;
        }
        PlayerState s = room.getPlayer(ss);
        return s != null && s.isAlive() && s.getRole() == Role.SEER;
    }

    /**
     * 女巫/预言家已死时仍进入对应阶段位；若无存活可操作者，在同一调用链内自动空过（等价超时 SKIP），避免卡死。
     */
    private HandleActionResult runAutopilotNightPhases(GameRoomState room, HandleActionResult current) {
        HandleActionResult r = current;
        for (int i = 0; i < 4; i++) {
            if (room.getPhase() == GamePhase.NIGHT_WITCH && !witchCanAct(room) && !room.isWitchActedThisNight()) {
                room.setWitchUsedSaveTonight(false);
                room.setWitchPoisonTargetTonight(null);
                room.setWitchActedThisNight(true);
                ActionAck auto = ActionAck.ok("女巫已出局或未在场，本夜跳过女巫阶段", GamePhase.NIGHT_WITCH, null);
                r = enterSeerPhaseAlways(room, auto);
                continue;
            }
            if (room.getPhase() == GamePhase.NIGHT_SEER && !seerCanAct(room) && !room.isSeerActedThisNight()) {
                room.setSeerActedThisNight(true);
                ActionAck auto = ActionAck.ok("预言家已出局或未在场，本夜跳过查验", GamePhase.NIGHT_SEER, null);
                r = finishNightAfterSeer(room, auto);
                break;
            }
            break;
        }
        return r;
    }

    /**
     * PRD 夜晚顺序位：始终进入女巫阶段（即使女巫已死，便于 B 推送 PHASE_SYNC 阶段位）。
     */
    private HandleActionResult enterNightWitch(GameRoomState room, ActionAck priorAck) {
        room.setPhase(GamePhase.NIGHT_WITCH);
        room.setWitchActedThisNight(false);
        if (witchCanAct(room)) {
            int ws = room.witchSeat();
            return HandleActionResult.of(priorAck, List.of(PhaseSyncBuilder.forPlayer(room, ws)));
        }
        return HandleActionResult.of(priorAck, syncsAllAlive(room));
    }

    /**
     * 始终进入预言家阶段位；预言家存活则等待 CHECK，否则仅广播阶段位后由 {@link #runAutopilotNightPhases} 空过。
     */
    private HandleActionResult enterSeerPhaseAlways(GameRoomState room, ActionAck priorAck) {
        room.setPhase(GamePhase.NIGHT_SEER);
        room.setSeerActedThisNight(false);
        if (seerCanAct(room)) {
            int ss = room.seerSeat();
            return HandleActionResult.of(priorAck, List.of(PhaseSyncBuilder.forPlayer(room, ss)));
        }
        return HandleActionResult.of(priorAck, syncsAllAlive(room));
    }

    private HandleActionResult handleNightWitch(GameRoomState room, PlayerState actor, GameActionCommand command) {
        if (actor.getRole() != Role.WITCH) {
            return failAck(ActionErrorCode.INVALID_ACTION, "Only witch acts in NIGHT_WITCH", room);
        }
        if (room.isWitchActedThisNight()) {
            return failAck(ActionErrorCode.INVALID_ACTION, "女巫本夜已行动", room);
        }

        return switch (command.action()) {
            case SKIP -> {
                room.setWitchUsedSaveTonight(false);
                room.setWitchPoisonTargetTonight(null);
                room.setWitchActedThisNight(true);
                ActionAck ack = ActionAck.ok("女巫跳过", room.getPhase(), null);
                yield runAutopilotNightPhases(room, enterSeerPhaseAlways(room, ack));
            }
            case SAVE -> {
                Integer kill = room.getPendingWolfKillTarget();
                if (kill == null) {
                    yield failAck(ActionErrorCode.INVALID_ACTION, "本夜无刀口可救", room);
                }
                if (!room.isWitchAntidoteRemaining()) {
                    yield failAck(ActionErrorCode.INVALID_ACTION, "解药已用尽", room);
                }
                if (room.getWitchPoisonTargetTonight() != null) {
                    yield failAck(ActionErrorCode.INVALID_ACTION, "已选毒药，不能再救", room);
                }
                room.setWitchUsedSaveTonight(true);
                room.setWitchPoisonTargetTonight(null);
                room.setWitchActedThisNight(true);
                ActionAck ack = ActionAck.ok("已使用解药", room.getPhase(), null);
                yield runAutopilotNightPhases(room, enterSeerPhaseAlways(room, ack));
            }
            case POISON -> {
                Integer t = command.target();
                if (t == null) {
                    yield failAck(ActionErrorCode.INVALID_TARGET, "毒杀需要 target", room);
                }
                if (!room.isWitchPoisonRemaining()) {
                    yield failAck(ActionErrorCode.INVALID_ACTION, "毒药已用尽", room);
                }
                PlayerState tgt = room.getPlayer(t);
                if (tgt == null || !tgt.isAlive()) {
                    yield failAck(ActionErrorCode.INVALID_TARGET, "目标必须存活", room);
                }
                if (Boolean.TRUE.equals(room.getWitchUsedSaveTonight())) {
                    yield failAck(ActionErrorCode.INVALID_ACTION, "同夜不能救后再毒", room);
                }
                room.setWitchUsedSaveTonight(false);
                room.setWitchPoisonTargetTonight(t);
                room.setWitchActedThisNight(true);
                ActionAck ack = ActionAck.ok("已使用毒药", room.getPhase(), null);
                yield runAutopilotNightPhases(room, enterSeerPhaseAlways(room, ack));
            }
            default -> failAck(ActionErrorCode.INVALID_ACTION,
                    "Action not allowed in NIGHT_WITCH: " + command.action(), room);
        };
    }

    private HandleActionResult handleNightSeer(GameRoomState room, PlayerState actor, GameActionCommand command) {
        if (actor.getRole() != Role.SEER) {
            return failAck(ActionErrorCode.INVALID_ACTION, "Only seer acts in NIGHT_SEER", room);
        }
        if (room.isSeerActedThisNight()) {
            return failAck(ActionErrorCode.INVALID_ACTION, "预言家本夜已查验", room);
        }
        if (command.action() != GameActionType.CHECK) {
            return failAck(ActionErrorCode.INVALID_ACTION, "NIGHT_SEER expects CHECK", room);
        }
        Integer t = command.target();
        if (t == null) {
            return failAck(ActionErrorCode.INVALID_TARGET, "查验需要 target", room);
        }
        if (t == actor.getPlayerId()) {
            return failAck(ActionErrorCode.INVALID_TARGET, "不能查验自己", room);
        }
        PlayerState tgt = room.getPlayer(t);
        if (tgt == null || !tgt.isAlive()) {
            return failAck(ActionErrorCode.INVALID_TARGET, "目标必须存活", room);
        }

        SeerCheckResult result = SeerCheckResult.forRole(tgt.getRole());
        room.setLastSeerCheckTarget(t);
        room.setLastSeerCheckResult(result);
        room.setSeerCheckTargetTonight(t);
        room.setSeerActedThisNight(true);

        String msg = result == SeerCheckResult.WOLF ? "查杀：狼人" : "查验：好人";
        ActionAck ack = ActionAck.ok(msg, room.getPhase(), null);
        return finishNightAfterSeer(room, ack);
    }

    private HandleActionResult finishNightAfterSeer(GameRoomState room, ActionAck priorAck) {
        NightResolver.applyNightDeaths(room);
        return afterDeathsEvaluate(room, priorAck);
    }

    private HandleActionResult afterDeathsEvaluate(GameRoomState room, ActionAck priorAck) {
        GameWinner w = WinChecker.evaluate(room);
        if (w != null) {
            room.setWinner(w);
            room.setPhase(GamePhase.GAME_OVER);
            room.setStatus(RoomStatus.ENDED);
            return HandleActionResult.of(priorAck, syncsAllAlive(room));
        }
        // HUNTER_SHOOT 仅在规则触发时出现（如被狼刀死可开枪，见 PRD R7～R9），非“每夜必经位”
        Integer hunterSeat = room.getHunterShooterSeat();
        if (hunterSeat != null) {
            room.setPhase(GamePhase.HUNTER_SHOOT);
            return HandleActionResult.of(priorAck, List.of(PhaseSyncBuilder.forPlayer(room, hunterSeat)));
        }
        enterDayDiscuss(room);
        return HandleActionResult.of(priorAck, syncsAllAlive(room));
    }

    private HandleActionResult handleHunterShoot(GameRoomState room, PlayerState actor, GameActionCommand command) {
        Integer hs = room.getHunterShooterSeat();
        if (hs == null || hs != actor.getPlayerId()) {
            return failAck(ActionErrorCode.INVALID_ACTION, "当前非猎人开枪阶段或不是你的回合", room);
        }
        return switch (command.action()) {
            case SKIP -> {
                PlayerState hunter = room.getPlayer(hs);
                if (hunter != null) {
                    hunter.setAlive(false);
                }
                room.setHunterShooterSeat(null);
                ActionAck ack = ActionAck.ok("猎人放弃开枪", room.getPhase(), null);
                yield afterHunterResolved(room, ack);
            }
            case SHOOT -> {
                Integer t = command.target();
                if (t == null) {
                    yield failAck(ActionErrorCode.INVALID_TARGET, "开枪需要 target", room);
                }
                PlayerState tgt = room.getPlayer(t);
                if (tgt == null || !tgt.isAlive()) {
                    yield failAck(ActionErrorCode.INVALID_TARGET, "目标必须存活", room);
                }
                tgt.setAlive(false);
                PlayerState hunter = room.getPlayer(hs);
                if (hunter != null) {
                    hunter.setAlive(false);
                }
                room.setHunterShooterSeat(null);
                ActionAck ack = ActionAck.ok("猎人已开枪", room.getPhase(), null);
                yield afterHunterResolved(room, ack);
            }
            default -> failAck(ActionErrorCode.INVALID_ACTION,
                    "HUNTER_SHOOT only SHOOT or SKIP", room);
        };
    }

    private HandleActionResult afterHunterResolved(GameRoomState room, ActionAck priorAck) {
        GameWinner w = WinChecker.evaluate(room);
        if (w != null) {
            room.setWinner(w);
            room.setPhase(GamePhase.GAME_OVER);
            room.setStatus(RoomStatus.ENDED);
            return HandleActionResult.of(priorAck, syncsAllAlive(room));
        }
        enterDayDiscuss(room);
        return HandleActionResult.of(priorAck, syncsAllAlive(room));
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
            return failAck(ActionErrorCode.INVALID_PHASE, "讨论已结束或未开始", room);
        }
        int expected = order.get(idx);
        if (actor.getPlayerId() != expected) {
            return failAck(ActionErrorCode.NOT_YOUR_TURN, "当前轮到 " + expected + " 号发言", room);
        }
        return switch (command.action()) {
            case SPEAK, SKIP_SPEAK -> {
                room.setDiscussIndex(idx + 1);
                ActionAck ack = ActionAck.ok("发言已记录", room.getPhase(), null);
                if (room.getDiscussIndex() >= order.size()) {
                    room.getDayVotes().clear();
                    room.setPhase(GamePhase.DAY_VOTE);
                }
                yield HandleActionResult.of(ack, syncsAllAlive(room));
            }
            default -> failAck(ActionErrorCode.INVALID_ACTION, "讨论阶段仅允许 SPEAK / SKIP_SPEAK", room);
        };
    }

    private HandleActionResult handleDayVote(GameRoomState room, PlayerState actor, GameActionCommand command) {
        if (!actor.isCanVote()) {
            return failAck(ActionErrorCode.INVALID_ACTION, "白痴翻牌后不可投票", room);
        }
        return switch (command.action()) {
            case VOTE, SKIP_VOTE -> {
                Integer target = command.action() == GameActionType.SKIP_VOTE ? null : command.target();
                room.getDayVotes().put(actor.getPlayerId(), target);
                ActionAck ack = ActionAck.ok("投票已记录", room.getPhase(), null);
                if (allCanVotePlayersSubmitted(room)) {
                    yield resolveDayVote(room, ack);
                }
                yield HandleActionResult.of(ack, syncsAllAlive(room));
            }
            default -> failAck(ActionErrorCode.INVALID_ACTION, "投票阶段仅允许 VOTE / SKIP_VOTE", room);
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
        room.setPhase(GamePhase.VOTE_RESULT);
        room.getDayVotes().clear();

        if (exileSeat != null) {
            PlayerState ex = room.getPlayer(exileSeat);
            if (ex != null && ex.isAlive()) {
                if (ex.getRole() == Role.IDIOT && !ex.isIdiotRevealed()) {
                    ex.setIdiotRevealed(true);
                    ex.setCanVote(false);
                } else if (ex.getRole() == Role.HUNTER) {
                    room.setHunterShooterSeat(exileSeat);
                    room.setPhase(GamePhase.HUNTER_SHOOT);
                    return HandleActionResult.of(priorAck, List.of(PhaseSyncBuilder.forPlayer(room, exileSeat)));
                } else {
                    ex.setAlive(false);
                }
            }
        }

        room.setPhase(GamePhase.CHECK_WIN);
        GameWinner w = WinChecker.evaluate(room);
        if (w != null) {
            room.setWinner(w);
            room.setPhase(GamePhase.GAME_OVER);
            room.setStatus(RoomStatus.ENDED);
            return HandleActionResult.of(priorAck, syncsAllAlive(room));
        }

        if (room.getHunterShooterSeat() != null) {
            int hid = room.getHunterShooterSeat();
            room.setPhase(GamePhase.HUNTER_SHOOT);
            return HandleActionResult.of(priorAck, List.of(PhaseSyncBuilder.forPlayer(room, hid)));
        }

        room.setRound(room.getRound() + 1);
        room.setPhase(GamePhase.NIGHT_START);
        enterNightWolf(room);
        return HandleActionResult.of(priorAck, syncsAllAlive(room));
    }

    private void enterNightWolf(GameRoomState room) {
        room.setPhase(GamePhase.NIGHT_WOLF);
        room.resetWolfNightState();
    }

    private List<PhaseSyncPayload> buildWolfPhaseSyncs(GameRoomState room) {
        List<PhaseSyncPayload> syncs = new ArrayList<>();
        for (int wolfId : room.aliveWolfIds()) {
            syncs.add(PhaseSyncBuilder.forPlayer(room, wolfId));
        }
        return syncs;
    }

    private static List<PhaseSyncPayload> syncsAllAlive(GameRoomState room) {
        List<PhaseSyncPayload> syncs = new ArrayList<>();
        for (int id : room.alivePlayerIds()) {
            syncs.add(PhaseSyncBuilder.forPlayer(room, id));
        }
        return syncs;
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
        static HandleActionResult of(ActionAck ack, List<PhaseSyncPayload> syncs) {
            return new HandleActionResult(ack, syncs);
        }
    }
}
