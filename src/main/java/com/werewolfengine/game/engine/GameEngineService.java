package com.werewolfengine.game.engine;

import com.werewolfengine.ai.api.AIService;
import com.werewolfengine.ai.api.PlayerIntent;
import com.werewolfengine.game.model.GameActionCommand;
import com.werewolfengine.game.model.GameActionType;
import com.werewolfengine.game.model.GamePhase;
import com.werewolfengine.game.model.GameRoomState;
import com.werewolfengine.game.observability.ActionLogEntry;
import com.werewolfengine.game.observability.ActionLogService;
import com.werewolfengine.game.orchestration.GamePhaseScheduler;
import com.werewolfengine.game.orchestration.MockGameRunner;
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
    private final AIService aiService;
    private final MockGameRunner mockGameRunner;
    private final ActionLogService actionLog;
    private final GamePhaseScheduler phaseScheduler;

    public GameEngineService(
            GameStateMachine stateMachine,
            AIService aiService,
            MockGameRunner mockGameRunner,
            ActionLogService actionLog,
            GamePhaseScheduler phaseScheduler
    ) {
        this.stateMachine = stateMachine;
        this.aiService = aiService;
        this.mockGameRunner = mockGameRunner;
        this.actionLog = actionLog;
        this.phaseScheduler = phaseScheduler;
    }

    public GameRoomState createDevRoom(String roomId) {
        if (roomId == null || roomId.isBlank()) {
            roomId = "r_" + UUID.randomUUID().toString().substring(0, 8);
        }
        GameRoomState room = stateMachine.createRoom(roomId);
        stateMachine.markAllReady(roomId);
        actionLog.clear(room.getRoomId());
        return room;
    }

    public GameStateMachine.StartGameResult startGame(String roomId) {
        return stateMachine.startGame(roomId);
    }

    public ActionResult submitAction(String roomId, GameActionCommand command) {
        GameRoomState before = stateMachine.getRoom(roomId).orElseThrow();
        int round = before.getRound();
        GamePhase phase = command.clientPhase() != null ? command.clientPhase() : before.getPhase();
        Integer effectiveTarget = command.action() == GameActionType.SAVE
                ? before.getPendingWolfKillTarget()
                : command.target();
        GameStateMachine.HandleActionResult result = stateMachine.handleAction(roomId, command);
        GameRoomState after = stateMachine.getRoom(roomId).orElse(before);
        actionLog.recordPlayerAction(roomId, round, phase, after, command, result.ack(), effectiveTarget);
        ActionAckPayload ack = stateMachine.toPayload(result.ack());
        return new ActionResult(ack, result.phaseSyncs());
    }

    /**
     * Advances {@link GamePhase#NIGHT_DEATH_ANNOUNCE} or {@link GamePhase#EXILE_DEATH_ANNOUNCE}
     * (timer / gateway); not a player GAME_ACTION.
     */
    public ActionResult advanceDayAnnounce(String roomId) {
        GameRoomState beforeRoom = stateMachine.getRoom(roomId).orElse(null);
        GamePhase before = beforeRoom != null ? beforeRoom.getPhase() : null;
        int round = beforeRoom != null ? beforeRoom.getRound() : 0;
        GameStateMachine.HandleActionResult result = stateMachine.advanceDayAnnounce(roomId);
        if (before != null && beforeRoom != null) {
            actionLog.recordSystemEvent(beforeRoom.getRoomId(), round, before, "advanceDayAnnounce", null);
        }
        return new ActionResult(stateMachine.toPayload(result.ack()), result.phaseSyncs());
    }

    public GamePhaseScheduler.TickResult tickPhase(String roomId) {
        return phaseScheduler.tick(roomId);
    }

    public List<ActionLogEntry> getActionLog(String roomId) {
        return actionLog.getLog(roomId);
    }

    /**
     * Runs AI (LLM + Mock fallback) until {@link GamePhase#GAME_OVER} or step limit.
     */
    public MockGameRunner.RunResult runMockAutoPlay(String roomId) {
        return mockGameRunner.runUntilGameOver(roomId);
    }

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
            aiService.decide(room, wid).ifPresent(intent -> {
                GameActionCommand cmd = new GameActionCommand(
                        wid,
                        intent.action(),
                        intent.target(),
                        GamePhase.NIGHT_WOLF,
                        intent.content()
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
