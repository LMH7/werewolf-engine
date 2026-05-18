package com.werewolfengine.game.hunter;

import com.werewolfengine.game.engine.GameStateMachine;
import com.werewolfengine.game.sync.PhaseSyncBuilder;
import com.werewolfengine.game.win.GameOutcome;
import com.werewolfengine.game.death.DeathBus;
import com.werewolfengine.game.lastwords.LastWordsFlow;
import com.werewolfengine.game.death.DeathCause;
import com.werewolfengine.game.death.DeathRecord;
import com.werewolfengine.game.model.ActionAck;
import com.werewolfengine.game.model.ActionErrorCode;
import com.werewolfengine.game.model.GameActionCommand;
import com.werewolfengine.game.model.GameActionType;
import com.werewolfengine.game.model.GamePhase;
import com.werewolfengine.game.model.GameRoomState;
import com.werewolfengine.game.model.PlayerState;
import com.werewolfengine.message.payload.PhaseSyncPayload;

import java.util.ArrayList;
import java.util.List;

/**
 * Hunter R7/R9 after announce (PRD §4.3.8.5, ADR-002).
 */
public final class HunterShootFlow {

    private final DeathBus deathBus;
    private final LastWordsFlow lastWordsFlow = new LastWordsFlow();

    public HunterShootFlow() {
        this(new DeathBus());
    }

    public HunterShootFlow(DeathBus deathBus) {
        this.deathBus = deathBus;
    }

    public GameStateMachine.HandleActionResult advanceNightDeathAnnounce(
            GameRoomState room,
            ActionAck priorAck,
            Runnable enterDayDiscuss
    ) {
        GameStateMachine.HandleActionResult early = GameOutcome.tryEndGame(room,
                ActionAck.ok("屠边已满足，对局结束", GamePhase.NIGHT_DEATH_ANNOUNCE, null));
        if (early != null) {
            return early;
        }
        if (lastWordsFlow.shouldEnterAfterNightAnnounce(room)) {
            room.setLastWordsAfterExile(false);
            ActionAck ack = ActionAck.ok("天亮公布结束，进入遗言", GamePhase.NIGHT_DEATH_ANNOUNCE, null);
            return lastWordsFlow.tryEnter(
                    room,
                    lastWordsFlow.nightLastWordsQueue(room),
                    ack,
                    () -> finishNightAfterAnnounce(room, enterDayDiscuss));
        }
        return finishNightAfterAnnounce(room, enterDayDiscuss);
    }

    public GameStateMachine.HandleActionResult advanceExileDeathAnnounce(
            GameRoomState room,
            ActionAck priorAck,
            java.util.function.Function<GameRoomState, GameStateMachine.HandleActionResult> continueAfterVote
    ) {
        GameStateMachine.HandleActionResult early = GameOutcome.tryEndGame(room,
                ActionAck.ok("屠边已满足，对局结束", GamePhase.EXILE_DEATH_ANNOUNCE, null));
        if (early != null) {
            return early;
        }
        if (lastWordsFlow.shouldEnterAfterExileAnnounce(room)) {
            room.setLastWordsAfterExile(true);
            ActionAck ack = ActionAck.ok("放逐公布结束，进入遗言", GamePhase.EXILE_DEATH_ANNOUNCE, null);
            return lastWordsFlow.tryEnter(
                    room,
                    lastWordsFlow.exileLastWordsQueue(room),
                    ack,
                    () -> finishExileAfterAnnounce(room, continueAfterVote));
        }
        return finishExileAfterAnnounce(room, continueAfterVote);
    }

    public GameStateMachine.HandleActionResult finishNightAfterAnnounce(
            GameRoomState room,
            Runnable enterDayDiscuss
    ) {
        Integer pending = room.getPendingHunterAfterAnnounce();
        room.setPendingHunterAfterAnnounce(null);
        if (pending != null) {
            room.setHunterShootAfterExile(false);
            return enterHunterShoot(room, pending, "遗言结束，进入猎人阶段");
        }
        ActionAck ack = ActionAck.ok("遗言结束，进入讨论", GamePhase.NIGHT_DEATH_ANNOUNCE, null);
        enterDayDiscuss.run();
        return GameStateMachine.HandleActionResult.of(ack, GameOutcome.syncsAllAlive(room));
    }

    public GameStateMachine.HandleActionResult finishExileAfterAnnounce(
            GameRoomState room,
            java.util.function.Function<GameRoomState, GameStateMachine.HandleActionResult> continueAfterVote
    ) {
        Integer pending = room.getPendingHunterAfterAnnounce();
        room.setPendingHunterAfterAnnounce(null);
        room.setExileAnnouncedSeat(null);
        if (pending != null) {
            room.setHunterShootAfterExile(true);
            return enterHunterShoot(room, pending, "遗言结束，进入猎人阶段");
        }
        ActionAck ack = ActionAck.ok("遗言结束", GamePhase.EXILE_DEATH_ANNOUNCE, null);
        return continueAfterVote.apply(room);
    }

    public GameStateMachine.HandleActionResult handleShoot(
            GameRoomState room,
            PlayerState actor,
            GameActionCommand command,
            Runnable enterDayDiscuss,
            java.util.function.Function<GameRoomState, GameStateMachine.HandleActionResult> continueAfterVote
    ) {
        Integer hs = room.getHunterShooterSeat();
        if (hs == null || hs != actor.getPlayerId()) {
            return fail(room, ActionErrorCode.INVALID_ACTION, "当前非猎人开枪阶段或不是你的回合");
        }
        return switch (command.action()) {
            case SKIP -> resolveSkip(room, hs, enterDayDiscuss, continueAfterVote);
            case SHOOT -> resolveShoot(room, hs, command.target(), enterDayDiscuss, continueAfterVote);
            default -> fail(room, ActionErrorCode.INVALID_ACTION, "HUNTER_SHOOT only SHOOT or SKIP");
        };
    }

    private GameStateMachine.HandleActionResult resolveSkip(
            GameRoomState room,
            int hunterSeat,
            Runnable enterDayDiscuss,
            java.util.function.Function<GameRoomState, GameStateMachine.HandleActionResult> continueAfterVote
    ) {
        room.setHunterShooterSeat(null);
        var ended = deathBus.apply(room, List.of(new DeathRecord(hunterSeat, DeathCause.HUNTER_SHOOT)));
        if (ended.gameEnded()) {
            ActionAck ack = ActionAck.ok("猎人放弃开枪", room.getPhase(), null);
            return GameStateMachine.HandleActionResult.of(ack, GameOutcome.syncsAllAlive(room));
        }
        ActionAck ack = ActionAck.ok("猎人放弃开枪", room.getPhase(), null);
        return afterResolved(room, ack, enterDayDiscuss, continueAfterVote);
    }

    private GameStateMachine.HandleActionResult resolveShoot(
            GameRoomState room,
            int hunterSeat,
            Integer target,
            Runnable enterDayDiscuss,
            java.util.function.Function<GameRoomState, GameStateMachine.HandleActionResult> continueAfterVote
    ) {
        if (target == null) {
            return fail(room, ActionErrorCode.INVALID_TARGET, "开枪需要 target");
        }
        PlayerState tgt = room.getPlayer(target);
        if (tgt == null || !tgt.isAlive()) {
            return fail(room, ActionErrorCode.INVALID_TARGET, "目标必须存活");
        }
        room.setHunterShooterSeat(null);
        List<DeathRecord> records = new ArrayList<>();
        records.add(new DeathRecord(target, DeathCause.HUNTER_SHOOT));
        records.add(new DeathRecord(hunterSeat, DeathCause.HUNTER_SHOOT));
        var ended = deathBus.apply(room, records);
        ActionAck ack = ActionAck.ok("猎人已开枪", room.getPhase(), null);
        if (ended.gameEnded()) {
            return GameStateMachine.HandleActionResult.of(ack, GameOutcome.syncsAllAlive(room));
        }
        return afterResolved(room, ack, enterDayDiscuss, continueAfterVote);
    }

    private GameStateMachine.HandleActionResult afterResolved(
            GameRoomState room,
            ActionAck priorAck,
            Runnable enterDayDiscuss,
            java.util.function.Function<GameRoomState, GameStateMachine.HandleActionResult> continueAfterVote
    ) {
        boolean afterExile = room.isHunterShootAfterExile();
        room.setHunterShootAfterExile(false);

        GameStateMachine.HandleActionResult ended = GameOutcome.tryEndGame(room, priorAck);
        if (ended != null) {
            return ended;
        }
        if (afterExile) {
            return continueAfterVote.apply(room);
        }
        enterDayDiscuss.run();
        return GameStateMachine.HandleActionResult.of(priorAck, GameOutcome.syncsAllAlive(room));
    }

    private static GameStateMachine.HandleActionResult enterHunterShoot(
            GameRoomState room,
            int hunterSeat,
            String message
    ) {
        room.setHunterShooterSeat(hunterSeat);
        room.setPhase(GamePhase.HUNTER_SHOOT);
        ActionAck ack = ActionAck.ok(message, room.getPhase(), null);
        List<PhaseSyncPayload> syncs = List.of(PhaseSyncBuilder.forPlayer(room, hunterSeat));
        return GameStateMachine.HandleActionResult.of(ack, syncs);
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
