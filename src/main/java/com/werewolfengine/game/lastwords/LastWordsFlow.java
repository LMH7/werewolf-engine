package com.werewolfengine.game.lastwords;

import com.werewolfengine.game.engine.GameStateMachine;
import com.werewolfengine.game.win.GameOutcome;
import com.werewolfengine.game.model.ActionAck;
import com.werewolfengine.game.model.ActionErrorCode;
import com.werewolfengine.game.model.GameActionCommand;
import com.werewolfengine.game.model.GameActionType;
import com.werewolfengine.game.model.GamePhase;
import com.werewolfengine.game.model.GameRoomState;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * R24 — last words after first-night announce or exile announce (PRD v1.0.12).
 */
public final class LastWordsFlow {

    public boolean shouldEnterAfterNightAnnounce(GameRoomState room) {
        return room.getRound() == 1 && !room.getLastNightDeaths().isEmpty();
    }

    public boolean shouldEnterAfterExileAnnounce(GameRoomState room) {
        return room.getExileAnnouncedSeat() != null;
    }

    public List<Integer> nightLastWordsQueue(GameRoomState room) {
        return new ArrayList<>(room.getLastNightDeaths());
    }

    public List<Integer> exileLastWordsQueue(GameRoomState room) {
        Integer seat = room.getExileAnnouncedSeat();
        if (seat == null) {
            return List.of();
        }
        return List.of(seat);
    }

    public GameStateMachine.HandleActionResult tryEnter(
            GameRoomState room,
            List<Integer> speakers,
            ActionAck priorAck,
            Supplier<GameStateMachine.HandleActionResult> onComplete
    ) {
        if (speakers.isEmpty()) {
            return onComplete.get();
        }
        room.getLastWordsOrder().clear();
        room.getLastWordsOrder().addAll(speakers);
        room.setLastWordsIndex(0);
        room.setPhase(GamePhase.LAST_WORDS);
        ActionAck ack = ActionAck.ok("进入遗言阶段", GamePhase.LAST_WORDS, null);
        return GameStateMachine.HandleActionResult.of(ack, GameOutcome.syncsAllAlive(room));
    }

    public GameStateMachine.HandleActionResult handleAction(
            GameRoomState room,
            GameActionCommand command,
            Supplier<GameStateMachine.HandleActionResult> onComplete
    ) {
        List<Integer> order = room.getLastWordsOrder();
        int idx = room.getLastWordsIndex();
        if (order.isEmpty() || idx >= order.size()) {
            return fail(room, ActionErrorCode.INVALID_PHASE, "遗言已结束或未开始");
        }
        int expected = order.get(idx);
        if (command.playerId() != expected) {
            return fail(room, ActionErrorCode.NOT_YOUR_TURN, "当前轮到 " + expected + " 号遗言");
        }
        return switch (command.action()) {
            case SPEAK, SKIP_SPEAK -> {
                room.setLastWordsIndex(idx + 1);
                ActionAck ack = ActionAck.ok("遗言已记录", room.getPhase(), null);
                if (room.getLastWordsIndex() >= order.size()) {
                    room.clearLastWords();
                    yield onComplete.get();
                }
                yield GameStateMachine.HandleActionResult.of(ack, GameOutcome.syncsAllAlive(room));
            }
            default -> fail(room, ActionErrorCode.INVALID_ACTION, "遗言阶段仅允许 SPEAK / SKIP_SPEAK");
        };
    }

    public static boolean isCurrentSpeaker(GameRoomState room, int playerId) {
        List<Integer> order = room.getLastWordsOrder();
        int idx = room.getLastWordsIndex();
        return room.getPhase() == GamePhase.LAST_WORDS
                && !order.isEmpty()
                && idx < order.size()
                && order.get(idx) == playerId;
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
