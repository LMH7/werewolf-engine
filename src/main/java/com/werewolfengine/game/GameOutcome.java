package com.werewolfengine.game;

import com.werewolfengine.game.model.ActionAck;
import com.werewolfengine.game.model.GamePhase;
import com.werewolfengine.game.model.GameRoomState;
import com.werewolfengine.game.model.GameWinner;
import com.werewolfengine.game.model.RoomStatus;
import com.werewolfengine.message.payload.PhaseSyncPayload;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class GameOutcome {

    private GameOutcome() {
    }

    public static Optional<GameWinner> evaluateAndEndIfMet(GameRoomState room) {
        GameWinner w = WinChecker.evaluate(room);
        if (w == null) {
            return Optional.empty();
        }
        endGame(room, w);
        return Optional.of(w);
    }

    public static void endGame(GameRoomState room, GameWinner winner) {
        room.setWinner(winner);
        room.setPendingHunterAfterAnnounce(null);
        room.setHunterShooterSeat(null);
        room.setHunterShootAfterExile(false);
        room.setPhase(GamePhase.GAME_OVER);
        room.setStatus(RoomStatus.ENDED);
    }

    public static List<PhaseSyncPayload> syncsAllAlive(GameRoomState room) {
        List<PhaseSyncPayload> syncs = new ArrayList<>();
        for (int id : room.alivePlayerIds()) {
            syncs.add(PhaseSyncBuilder.forPlayer(room, id));
        }
        return syncs;
    }

    public static GameStateMachine.HandleActionResult tryEndGame(GameRoomState room, ActionAck priorAck) {
        return evaluateAndEndIfMet(room)
                .map(w -> GameStateMachine.HandleActionResult.of(priorAck, syncsAllAlive(room)))
                .orElse(null);
    }
}
