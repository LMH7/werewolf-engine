package com.werewolfengine.game;

import com.werewolfengine.ai.MockAIPlayer;
import com.werewolfengine.ai.PlayerIntent;
import com.werewolfengine.game.model.GameActionCommand;
import com.werewolfengine.game.model.GamePhase;
import com.werewolfengine.game.model.GameRoomState;
import com.werewolfengine.message.payload.ActionAckPayload;
import com.werewolfengine.message.payload.PhaseSyncPayload;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Facade for B (gateway/room): room lifecycle + actions + phase snapshots.
 */
@Service
public class GameEngineService {

    private final GameStateMachine stateMachine;
    private final MockAIPlayer mockAIPlayer;

    public GameEngineService(GameStateMachine stateMachine, MockAIPlayer mockAIPlayer) {
        this.stateMachine = stateMachine;
        this.mockAIPlayer = mockAIPlayer;
    }

    public GameRoomState createDevRoom(String roomId) {
        if (roomId == null || roomId.isBlank()) {
            roomId = "r_" + UUID.randomUUID().toString().substring(0, 8);
        }
        GameRoomState room = stateMachine.createRoom(roomId);
        stateMachine.markAllReady(roomId);
        return room;
    }

    public GameStateMachine.StartGameResult startGame(String roomId) {
        return stateMachine.startGame(roomId);
    }

    public ActionResult submitAction(String roomId, GameActionCommand command) {
        GameStateMachine.HandleActionResult result = stateMachine.handleAction(roomId, command);
        ActionAckPayload ack = stateMachine.toPayload(result.ack());
        return new ActionResult(ack, result.phaseSyncs());
    }

    /**
     * Runs Mock AI until every alive wolf has a KILL vote or phase leaves {@link GamePhase#NIGHT_WOLF}.
     */
    public List<ActionResult> runMockWolfActions(String roomId) {
        List<ActionResult> results = new ArrayList<>();
        for (int safety = 0; safety < 16; safety++) {
            GameRoomState room = stateMachine.getRoom(roomId)
                    .orElseThrow(() -> new IllegalArgumentException("Room not found: " + roomId));
            if (room.getPhase() != GamePhase.NIGHT_WOLF) {
                return results;
            }
            int wolfToAct = -1;
            for (int wolfId : room.aliveWolfIds()) {
                if (!room.getWolfKillVotes().containsKey(wolfId)) {
                    wolfToAct = wolfId;
                    break;
                }
            }
            if (wolfToAct < 0) {
                return results;
            }
            final int wid = wolfToAct;
            mockAIPlayer.decide(room, wid).ifPresent(intent -> {
                GameActionCommand cmd = new GameActionCommand(
                        wid,
                        intent.action(),
                        intent.target(),
                        GamePhase.NIGHT_WOLF
                );
                results.add(submitAction(roomId, cmd));
            });
        }
        return results;
    }

    public RoomSnapshot getSnapshot(String roomId) {
        GameRoomState room = stateMachine.getRoom(roomId)
                .orElseThrow(() -> new IllegalArgumentException("Room not found: " + roomId));
        Map<Integer, Integer> votes = Map.copyOf(room.getWolfKillVotes());
        List<PhaseSyncPayload> wolfSyncs = new ArrayList<>();
        for (int wolfId : room.aliveWolfIds()) {
            wolfSyncs.add(stateMachine.buildPhaseSync(roomId, wolfId));
        }
        return new RoomSnapshot(room, votes, wolfSyncs);
    }

    public record ActionResult(ActionAckPayload ack, List<PhaseSyncPayload> phaseSyncs) {
    }

    public record RoomSnapshot(
            GameRoomState room,
            Map<Integer, Integer> wolfKillVotes,
            List<PhaseSyncPayload> wolfPhaseSyncs
    ) {
    }
}
