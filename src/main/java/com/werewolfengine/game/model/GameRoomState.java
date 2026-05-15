package com.werewolfengine.game.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * In-memory authoritative room state (MVP single-instance).
 */
public final class GameRoomState {

    public static final int SEAT_COUNT = 12;

    private final String roomId;
    private RoomStatus status;
    private GamePhase phase;
    private int round;
    private boolean wolfChatInPhase;
    private final Map<Integer, PlayerState> players;
    /** Wolf seat -> kill target seat (votes collected until phase ends). */
    private final Map<Integer, Integer> wolfKillVotes;

    public GameRoomState(String roomId) {
        this.roomId = roomId;
        this.status = RoomStatus.WAITING;
        this.phase = GamePhase.WAITING;
        this.round = 0;
        this.wolfChatInPhase = false;
        this.players = new LinkedHashMap<>();
        this.wolfKillVotes = new LinkedHashMap<>();
        for (int seat = 1; seat <= SEAT_COUNT; seat++) {
            players.put(seat, new PlayerState(seat));
        }
    }

    public String getRoomId() {
        return roomId;
    }

    public RoomStatus getStatus() {
        return status;
    }

    public void setStatus(RoomStatus status) {
        this.status = status;
    }

    public GamePhase getPhase() {
        return phase;
    }

    public void setPhase(GamePhase phase) {
        this.phase = phase;
    }

    public int getRound() {
        return round;
    }

    public void setRound(int round) {
        this.round = round;
    }

    public boolean isWolfChatInPhase() {
        return wolfChatInPhase;
    }

    public void setWolfChatInPhase(boolean wolfChatInPhase) {
        this.wolfChatInPhase = wolfChatInPhase;
    }

    public Map<Integer, PlayerState> getPlayers() {
        return players;
    }

    public PlayerState getPlayer(int playerId) {
        return players.get(playerId);
    }

    public Map<Integer, Integer> getWolfKillVotes() {
        return wolfKillVotes;
    }

    public List<Integer> alivePlayerIds() {
        return players.values().stream()
                .filter(PlayerState::isAlive)
                .map(PlayerState::getPlayerId)
                .sorted()
                .collect(Collectors.toList());
    }

    public List<Integer> aliveWolfIds() {
        return players.values().stream()
                .filter(p -> p.isAlive() && p.getRole() == Role.WEREWOLF)
                .map(PlayerState::getPlayerId)
                .sorted()
                .collect(Collectors.toList());
    }

    public void clearWolfKillVotes() {
        wolfKillVotes.clear();
    }

    public void resetWolfNightState() {
        wolfChatInPhase = false;
        wolfKillVotes.clear();
    }
}
