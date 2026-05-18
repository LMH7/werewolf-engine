package com.werewolfengine.game.night;

import com.werewolfengine.game.engine.GameStateMachine;
import com.werewolfengine.game.sync.PhaseSyncBuilder;
import com.werewolfengine.game.win.GameOutcome;
import com.werewolfengine.game.death.DeathBus;
import com.werewolfengine.game.observability.ActionLogService;
import com.werewolfengine.game.observability.PerceptionLogEvents;
import com.werewolfengine.game.model.ActionAck;
import com.werewolfengine.game.model.ActionErrorCode;
import com.werewolfengine.game.model.GameActionCommand;
import com.werewolfengine.game.model.GameActionType;
import com.werewolfengine.game.model.GamePhase;
import com.werewolfengine.game.model.GameRoomState;
import com.werewolfengine.game.model.PlayerState;
import com.werewolfengine.game.model.Role;
import com.werewolfengine.game.model.SeerCheckResult;
import com.werewolfengine.message.payload.PhaseSyncPayload;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Night-phase actions for {@link NightSkillPipeline} (wolf ??? seer ??? witch).
 */
public final class NightActions {

    private final DeathBus deathBus;
    private final WolfKillResolvedListener wolfKillResolvedListener;
    private final ActionLogService actionLog;

    public NightActions(DeathBus deathBus) {
        this(deathBus, null, null);
    }

    public NightActions(DeathBus deathBus, WolfKillResolvedListener wolfKillResolvedListener) {
        this(deathBus, wolfKillResolvedListener, null);
    }

    public NightActions(
            DeathBus deathBus,
            WolfKillResolvedListener wolfKillResolvedListener,
            ActionLogService actionLog
    ) {
        this.deathBus = deathBus;
        this.wolfKillResolvedListener = wolfKillResolvedListener;
        this.actionLog = actionLog;
    }

    public GameStateMachine.HandleActionResult handle(
            GameRoomState room,
            PlayerState actor,
            GameActionCommand command
    ) {
        return switch (room.getPhase()) {
            case NIGHT_WOLF -> handleNightWolf(room, actor, command);
            case NIGHT_SEER -> handleNightSeer(room, actor, command);
            case NIGHT_WITCH -> handleNightWitch(room, actor, command);
            default -> fail(room, ActionErrorCode.INVALID_PHASE,
                    "No night action in phase " + room.getPhase());
        };
    }

    public void enterNightWolf(GameRoomState room) {
        room.setPhase(GamePhase.NIGHT_WOLF);
        room.resetWolfNightState();
    }

    public List<PhaseSyncPayload> buildWolfPhaseSyncs(GameRoomState room) {
        List<PhaseSyncPayload> syncs = new ArrayList<>();
        for (int wolfId : room.aliveWolfIds()) {
            syncs.add(PhaseSyncBuilder.forPlayer(room, wolfId));
        }
        return syncs;
    }

    private GameStateMachine.HandleActionResult handleNightWolf(
            GameRoomState room,
            PlayerState actor,
            GameActionCommand command
    ) {
        return switch (command.action()) {
            case KILL -> handleKill(room, actor, command.target());
            case WOLF_CHAT -> handleWolfChat(room, actor);
            default -> fail(room, ActionErrorCode.INVALID_ACTION,
                    "Action not allowed in NIGHT_WOLF: " + command.action());
        };
    }

    private GameStateMachine.HandleActionResult handleKill(GameRoomState room, PlayerState actor, Integer targetId) {
        if (actor.getRole() != Role.WEREWOLF) {
            return fail(room, ActionErrorCode.INVALID_ACTION, "Only wolves can KILL");
        }
        if (targetId == null) {
            return fail(room, ActionErrorCode.INVALID_TARGET, "KILL requires target");
        }
        PlayerState target = room.getPlayer(targetId);
        if (target == null || !target.isAlive()) {
            return fail(room, ActionErrorCode.INVALID_TARGET, "Target must be alive");
        }
        if (target.getRole() == Role.WEREWOLF && !room.isWolfChatInPhase()) {
            return fail(room, ActionErrorCode.WOLF_CHAT_REQUIRED,
                    "??????????????????????????????????????????????????????????????????");
        }

        room.getWolfKillVotes().put(actor.getPlayerId(), targetId);
        room.appendWolfKillEvent(actor.getPlayerId(), targetId);

        ActionAck ack = ActionAck.ok("?????????????", room.getPhase(), "WAITING_WOLF_CONSENSUS");
        Optional<GameStateMachine.HandleActionResult> transition = tryAdvanceAfterAllWolvesVoted(room);
        return transition.orElseGet(() -> GameStateMachine.HandleActionResult.of(ack, List.of()));
    }

    private GameStateMachine.HandleActionResult handleWolfChat(GameRoomState room, PlayerState actor) {
        if (actor.getRole() != Role.WEREWOLF) {
            return fail(room, ActionErrorCode.INVALID_ACTION, "Only wolves can WOLF_CHAT");
        }
        room.setWolfChatInPhase(true);
        ActionAck ack = ActionAck.ok("??????????????", room.getPhase(), null);
        return GameStateMachine.HandleActionResult.of(ack, buildWolfPhaseSyncs(room));
    }

    private Optional<GameStateMachine.HandleActionResult> tryAdvanceAfterAllWolvesVoted(GameRoomState room) {
        List<Integer> wolves = room.aliveWolfIds();
        if (wolves.isEmpty()) {
            return Optional.empty();
        }
        for (int w : wolves) {
            if (!room.getWolfKillVotes().containsKey(w)) {
                return Optional.empty();
            }
        }

        Map<Integer, Integer> votesSnapshot = Map.copyOf(room.getWolfKillVotes());
        int resolved = WolfVoteResolver.resolveKillTarget(room);
        room.setPendingWolfKillTarget(resolved);
        if (wolfKillResolvedListener != null) {
            wolfKillResolvedListener.onResolved(room, resolved, votesSnapshot);
        }
        room.clearWolfVotesAndLog();

        ActionAck ack = ActionAck.ok("???????????????????????", GamePhase.NIGHT_SEER, null);
        return Optional.of(runAutopilotNightPhases(room, enterNightSeer(room, ack)));
    }

    private GameStateMachine.HandleActionResult runAutopilotNightPhases(
            GameRoomState room,
            GameStateMachine.HandleActionResult current
    ) {
        GameStateMachine.HandleActionResult r = current;
        for (int i = 0; i < 6; i++) {
            if (room.getPhase() == GamePhase.NIGHT_SEER && !seerCanAct(room) && !room.isSeerActedThisNight()) {
                room.setSeerActedThisNight(true);
                ActionAck auto = ActionAck.ok("??????????????????????????????????????", GamePhase.NIGHT_SEER, null);
                r = enterNightWitch(room, auto);
                continue;
            }
            if (room.getPhase() == GamePhase.NIGHT_WITCH && !witchCanAct(room) && !room.isWitchActedThisNight()) {
                room.setWitchUsedSaveTonight(false);
                room.setWitchPoisonTargetTonight(null);
                room.setWitchActedThisNight(true);
                ActionAck auto = ActionAck.ok("????????????????????????????????????", GamePhase.NIGHT_WITCH, null);
                r = finishNightAfterWitch(room, auto);
                break;
            }
            break;
        }
        return r;
    }

    private GameStateMachine.HandleActionResult enterNightWitch(GameRoomState room, ActionAck priorAck) {
        room.setPhase(GamePhase.NIGHT_WITCH);
        room.setWitchActedThisNight(false);
        if (witchCanAct(room)) {
            int ws = room.witchSeat();
            return GameStateMachine.HandleActionResult.of(priorAck, List.of(PhaseSyncBuilder.forPlayer(room, ws)));
        }
        return GameStateMachine.HandleActionResult.of(priorAck, GameOutcome.syncsAllAlive(room));
    }

    private GameStateMachine.HandleActionResult enterNightSeer(GameRoomState room, ActionAck priorAck) {
        room.setPhase(GamePhase.NIGHT_SEER);
        room.setSeerActedThisNight(false);
        if (seerCanAct(room)) {
            int ss = room.seerSeat();
            return GameStateMachine.HandleActionResult.of(priorAck, List.of(PhaseSyncBuilder.forPlayer(room, ss)));
        }
        return GameStateMachine.HandleActionResult.of(priorAck, GameOutcome.syncsAllAlive(room));
    }

    private GameStateMachine.HandleActionResult handleNightWitch(
            GameRoomState room,
            PlayerState actor,
            GameActionCommand command
    ) {
        if (actor.getRole() != Role.WITCH) {
            return fail(room, ActionErrorCode.INVALID_ACTION, "Only witch acts in NIGHT_WITCH");
        }
        if (room.isWitchActedThisNight()) {
            return fail(room, ActionErrorCode.INVALID_ACTION, "?????????????");
        }

        return switch (command.action()) {
            case SKIP -> {
                room.setWitchUsedSaveTonight(false);
                room.setWitchPoisonTargetTonight(null);
                room.setWitchActedThisNight(true);
                ActionAck ack = ActionAck.ok("?????", room.getPhase(), null);
                yield runAutopilotNightPhases(room, finishNightAfterWitch(room, ack));
            }
            case SAVE -> {
                Integer kill = room.getPendingWolfKillTarget();
                if (kill == null) {
                    yield fail(room, ActionErrorCode.INVALID_ACTION, "????????????????");
                }
                if (!room.isWitchAntidoteRemaining()) {
                    yield fail(room, ActionErrorCode.INVALID_ACTION, "???????");
                }
                if (room.getWitchPoisonTargetTonight() != null) {
                    yield fail(room, ActionErrorCode.INVALID_ACTION, "???????????????????");
                }
                room.setWitchUsedSaveTonight(true);
                room.setWitchPoisonTargetTonight(null);
                room.setWitchActedThisNight(true);
                ActionAck ack = ActionAck.ok("???????", room.getPhase(), null);
                yield runAutopilotNightPhases(room, finishNightAfterWitch(room, ack));
            }
            case POISON -> {
                Integer t = command.target();
                if (t == null) {
                    yield fail(room, ActionErrorCode.INVALID_TARGET, "???????? target");
                }
                if (!room.isWitchPoisonRemaining()) {
                    yield fail(room, ActionErrorCode.INVALID_ACTION, "????????");
                }
                PlayerState tgt = room.getPlayer(t);
                if (tgt == null || !tgt.isAlive()) {
                    yield fail(room, ActionErrorCode.INVALID_TARGET, "???????????");
                }
                if (Boolean.TRUE.equals(room.getWitchUsedSaveTonight())) {
                    yield fail(room, ActionErrorCode.INVALID_ACTION, "??????????????????");
                }
                room.setWitchUsedSaveTonight(false);
                room.setWitchPoisonTargetTonight(t);
                room.setWitchActedThisNight(true);
                ActionAck ack = ActionAck.ok("????????", room.getPhase(), null);
                yield runAutopilotNightPhases(room, finishNightAfterWitch(room, ack));
            }
            default -> fail(room, ActionErrorCode.INVALID_ACTION,
                    "Action not allowed in NIGHT_WITCH: " + command.action());
        };
    }

    private GameStateMachine.HandleActionResult handleNightSeer(
            GameRoomState room,
            PlayerState actor,
            GameActionCommand command
    ) {
        if (actor.getRole() != Role.SEER) {
            return fail(room, ActionErrorCode.INVALID_ACTION, "Only seer acts in NIGHT_SEER");
        }
        if (room.isSeerActedThisNight()) {
            return fail(room, ActionErrorCode.INVALID_ACTION, "????????????????");
        }
        if (command.action() != GameActionType.CHECK) {
            return fail(room, ActionErrorCode.INVALID_ACTION, "NIGHT_SEER expects CHECK");
        }
        Integer t = command.target();
        if (t == null) {
            return fail(room, ActionErrorCode.INVALID_TARGET, "????????? target");
        }
        if (t == actor.getPlayerId()) {
            return fail(room, ActionErrorCode.INVALID_TARGET, "?????????????");
        }
        PlayerState tgt = room.getPlayer(t);
        if (tgt == null || !tgt.isAlive()) {
            return fail(room, ActionErrorCode.INVALID_TARGET, "???????????");
        }

        SeerCheckResult result = SeerCheckResult.forRole(tgt.getRole());
        room.setLastSeerCheckTarget(t);
        room.setLastSeerCheckResult(result);
        room.setSeerCheckTargetTonight(t);
        room.setSeerActedThisNight(true);

        String msg = result == SeerCheckResult.WOLF ? "???????????" : "?????????";
        ActionAck ack = ActionAck.ok(msg, room.getPhase(), null);
        return runAutopilotNightPhases(room, enterNightWitch(room, ack));
    }

    public GameStateMachine.HandleActionResult finishNightAfterWitch(GameRoomState room, ActionAck priorAck) {
        if (NightResolver.applyNightDeaths(room, deathBus)) {
            return GameStateMachine.HandleActionResult.of(priorAck, GameOutcome.syncsAllAlive(room));
        }
        PerceptionLogEvents.nightDeaths(actionLog, room);
        room.setPhase(GamePhase.NIGHT_DEATH_ANNOUNCE);
        return GameStateMachine.HandleActionResult.of(priorAck, GameOutcome.syncsAllAlive(room));
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

    private static GameStateMachine.HandleActionResult fail(
            GameRoomState room,
            ActionErrorCode code,
            String msg
    ) {
        return GameStateMachine.HandleActionResult.of(
                ActionAck.fail(code, msg, room.getPhase()),
                List.of()
        );
    }
}
