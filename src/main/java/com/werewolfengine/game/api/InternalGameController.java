package com.werewolfengine.game.api;

import com.werewolfengine.game.engine.GameEngineService;
import com.werewolfengine.game.orchestration.GamePhaseScheduler;
import com.werewolfengine.game.engine.GameStateMachine;
import com.werewolfengine.game.orchestration.MockGameRunner;
import com.werewolfengine.game.observability.ActionLogEntry;
import com.werewolfengine.game.model.GameActionCommand;
import com.werewolfengine.game.model.GameRoomState;
import com.werewolfengine.game.model.PlayerState;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Temporary HTTP bridge for B (gateway/room) — Week1 Day1-2 only.
 * Not authenticated; do not expose publicly in production.
 */
@RestController
@RequestMapping("/internal/game")
public class InternalGameController {

    private final GameEngineService gameEngine;

    public InternalGameController(GameEngineService gameEngine) {
        this.gameEngine = gameEngine;
    }

    @PostMapping("/rooms")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createRoom(@RequestBody(required = false) CreateRoomRequest request) {
        String roomId = request != null ? request.roomId() : null;
        GameRoomState room = gameEngine.createDevRoom(roomId);
        return Map.of(
                "roomId", room.getRoomId(),
                "status", room.getStatus(),
                "phase", room.getPhase(),
                "ready", true
        );
    }

    @PostMapping("/rooms/{roomId}/start")
    public Map<String, Object> start(@PathVariable String roomId) {
        GameStateMachine.StartGameResult result = gameEngine.startGame(roomId);
        if (!result.success()) {
            return Map.of(
                    "success", false,
                    "code", result.errorCode(),
                    "message", result.message()
            );
        }
        return Map.of(
                "success", true,
                "phase", result.phase(),
                "phaseSyncs", result.phaseSyncs()
        );
    }

    @PostMapping("/rooms/{roomId}/actions")
    public Map<String, Object> submitAction(
            @PathVariable String roomId,
            @RequestBody SubmitActionRequest request
    ) {
        GameActionCommand command = new GameActionCommand(
                request.playerId(),
                request.action(),
                request.target(),
                request.phase(),
                request.content()
        );
        GameEngineService.ActionResult result = gameEngine.submitAction(roomId, command);
        return Map.of(
                "ack", result.ack(),
                "phaseSyncs", result.phaseSyncs()
        );
    }

    @PostMapping("/rooms/{roomId}/advance-announce")
    public Map<String, Object> advanceAnnounce(@PathVariable String roomId) {
        GameEngineService.ActionResult result = gameEngine.advanceDayAnnounce(roomId);
        return Map.of(
                "ack", result.ack(),
                "phaseSyncs", result.phaseSyncs()
        );
    }

    @PostMapping("/rooms/{roomId}/phase-tick")
    public Map<String, Object> phaseTick(@PathVariable String roomId) {
        GamePhaseScheduler.TickResult tick = gameEngine.tickPhase(roomId);
        return Map.of(
                "status", tick.status(),
                "phase", tick.phase(),
                "detail", tick.detail()
        );
    }

    @GetMapping("/rooms/{roomId}/action-log")
    public Map<String, Object> actionLog(@PathVariable String roomId) {
        List<ActionLogEntry> entries = gameEngine.getActionLog(roomId);
        return Map.of("roomId", roomId, "entries", entries);
    }

    @PostMapping("/rooms/{roomId}/mock-auto-play")
    public Map<String, Object> mockAutoPlay(@PathVariable String roomId) {
        MockGameRunner.RunResult result = gameEngine.runMockAutoPlay(roomId);
        return Map.of(
                "outcome", result.outcome(),
                "steps", result.steps(),
                "winner", result.winner() != null ? result.winner() : "",
                "phase", result.phase(),
                "mode", "ai-llm-with-mock-fallback"
        );
    }

    @PostMapping("/rooms/{roomId}/mock-wolves")
    public Map<String, Object> mockWolves(@PathVariable String roomId) {
        List<GameEngineService.ActionResult> results = gameEngine.runMockWolfActions(roomId);
        return Map.of(
                "results", results.stream()
                        .map(r -> Map.of("ack", r.ack(), "phaseSyncs", r.phaseSyncs()))
                        .toList()
        );
    }

    @GetMapping("/rooms/{roomId}")
    public Map<String, Object> snapshot(@PathVariable String roomId) {
        GameEngineService.RoomSnapshot snap = gameEngine.getSnapshot(roomId);
        GameRoomState room = snap.room();

        Map<Integer, Map<String, Object>> players = room.getPlayers().values().stream()
                .collect(Collectors.toMap(
                        PlayerState::getPlayerId,
                        p -> {
                            Map<String, Object> m = new LinkedHashMap<>();
                            m.put("alive", p.isAlive());
                            m.put("ready", p.isReady());
                            if (p.getRole() != null) {
                                m.put("role", p.getRole());
                            }
                            return m;
                        },
                        (a, b) -> a,
                        LinkedHashMap::new
                ));

        return Map.of(
                "roomId", room.getRoomId(),
                "status", room.getStatus(),
                "phase", room.getPhase(),
                "round", room.getRound(),
                "wolfChatInPhase", room.isWolfChatInPhase(),
                "wolfKillVotes", snap.wolfKillVotes(),
                "alivePlayers", room.alivePlayerIds(),
                "players", players,
                "wolfPhaseSyncs", snap.wolfPhaseSyncs()
        );
    }
}
