package com.werewolfengine.game.observability;

import com.werewolfengine.game.model.ActionAck;
import com.werewolfengine.game.model.GameActionCommand;
import com.werewolfengine.game.model.GameActionType;
import com.werewolfengine.game.model.GamePhase;
import com.werewolfengine.game.model.GameRoomState;
import com.werewolfengine.game.model.PlayerState;
import com.werewolfengine.game.model.Role;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-memory action log per room (PRD §4.7.3). B may persist to {@code game_record.action_log} later.
 */
@Service
public class ActionLogService {

    private final ConcurrentHashMap<String, CopyOnWriteArrayList<ActionLogEntry>> logs = new ConcurrentHashMap<>();

    /**
     * Records a player action at the phase/round when it was submitted (not after state transitions).
     */
    public void recordPlayerAction(
            String roomId,
            int round,
            GamePhase phase,
            GameRoomState roomForRole,
            GameActionCommand command,
            ActionAck ack,
            Integer effectiveTarget
    ) {
        if (roomId == null || command == null || ack == null || phase == null) {
            return;
        }
        GamePhase logPhase = command.clientPhase() != null ? command.clientPhase() : phase;
        PlayerState actor = roomForRole != null ? roomForRole.getPlayer(command.playerId()) : null;
        Role role = actor != null ? actor.getRole() : null;
        append(roomId, new ActionLogEntry(
                roomId,
                round,
                logPhase,
                command.playerId(),
                role,
                command.action(),
                effectiveTarget != null ? effectiveTarget : command.target(),
                command.content(),
                ack.success(),
                System.currentTimeMillis(),
                null,
                null
        ));
    }

    /** @deprecated use {@link #recordPlayerAction} with explicit phase/round */
    @Deprecated
    public void recordAction(GameRoomState room, GameActionCommand command, ActionAck ack) {
        if (room == null) {
            return;
        }
        recordPlayerAction(
                room.getRoomId(),
                room.getRound(),
                room.getPhase(),
                room,
                command,
                ack,
                resolveEffectiveTarget(room, command)
        );
    }

    public void recordSystemEvent(String roomId, int round, GamePhase phase, String message, Integer target) {
        if (roomId == null || phase == null || message == null) {
            return;
        }
        append(roomId, new ActionLogEntry(
                roomId,
                round,
                phase,
                0,
                null,
                null,
                target,
                message,
                true,
                System.currentTimeMillis(),
                null,
                null
        ));
    }

    public void recordSystemPhase(GameRoomState room, GamePhase phase, String message) {
        if (room == null) {
            return;
        }
        recordSystemEvent(room.getRoomId(), room.getRound(), phase, message, null);
    }

    private static Integer resolveEffectiveTarget(GameRoomState room, GameActionCommand command) {
        if (room == null || command == null) {
            return null;
        }
        return switch (command.action()) {
            case SAVE -> room.getPendingWolfKillTarget();
            default -> command.target();
        };
    }

    public void recordAiThinking(String roomId, int playerId, Role role, GamePhase phase, int round,
                                 GameActionType action, String thinking, String modelId) {
        if (thinking == null || thinking.isBlank()) {
            return;
        }
        append(roomId, new ActionLogEntry(
                roomId,
                round,
                phase,
                playerId,
                role,
                action,
                null,
                null,
                true,
                System.currentTimeMillis(),
                thinking,
                modelId
        ));
    }

    public List<ActionLogEntry> getLog(String roomId) {
        CopyOnWriteArrayList<ActionLogEntry> list = logs.get(roomId);
        return list == null ? List.of() : List.copyOf(list);
    }

    public void clear(String roomId) {
        logs.remove(roomId);
    }

    private void append(String roomId, ActionLogEntry entry) {
        logs.computeIfAbsent(roomId, k -> new CopyOnWriteArrayList<>()).add(entry);
    }
}
