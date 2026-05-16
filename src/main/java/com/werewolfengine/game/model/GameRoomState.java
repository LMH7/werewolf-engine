package com.werewolfengine.game.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * In-memory authoritative room state (MVP single-instance).
 */
public final class GameRoomState {

    public static final int SEAT_COUNT = 12;

    public record WolfKillEvent(int wolfId, int targetId, long seq) {
    }

    private final String roomId;
    private RoomStatus status;
    private GamePhase phase;
    private int round;
    private boolean wolfChatInPhase;
    private final Map<Integer, PlayerState> players;
    /** Wolf seat -> kill target seat (votes collected until phase ends). */
    private final Map<Integer, Integer> wolfKillVotes;
    private final List<WolfKillEvent> wolfKillEventLog = new ArrayList<>();
    private long wolfKillSeq;

    /** Wolf pack kill target for this night after R10 (before witch save). */
    private Integer pendingWolfKillTarget;

    private boolean witchAntidoteRemaining = true;
    private boolean witchPoisonRemaining = true;

    private Boolean witchUsedSaveTonight;
    private Integer witchPoisonTargetTonight;

    private Integer seerCheckTargetTonight;

    private SeerCheckResult lastSeerCheckResult;
    private Integer lastSeerCheckTarget;

    private List<Integer> lastNightDeaths = new ArrayList<>();

    private SpeakDirection speakDirection;
    private int speakAnchorSeat;
    private final List<Integer> discussOrder = new ArrayList<>();
    private int discussIndex;

    private final Map<Integer, Integer> dayVotes = new LinkedHashMap<>();

    /** Seat of hunter who must shoot after night or day exile; cleared after HUNTER_SHOOT. */
    private Integer hunterShooterSeat;

    private GameWinner winner;

    /** Witch has submitted SKIP / SAVE / POISON for this night. */
    private boolean witchActedThisNight;

    /** Seer has submitted CHECK for this night. */
    private boolean seerActedThisNight;

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

    /** Full lobby reset (optional). */
    public void resetMatchStateForNewGame() {
        prepareMatchStart();
        for (PlayerState p : players.values()) {
            p.setReady(false);
            p.setRole(null);
        }
    }

    /**
     * Before ROLE_ASSIGN on start: alive players, bottles, no votes; does not clear {@code ready}.
     */
    public void prepareMatchStart() {
        wolfChatInPhase = false;
        wolfKillVotes.clear();
        wolfKillEventLog.clear();
        wolfKillSeq = 0;
        pendingWolfKillTarget = null;
        witchAntidoteRemaining = true;
        witchPoisonRemaining = true;
        witchUsedSaveTonight = null;
        witchPoisonTargetTonight = null;
        seerCheckTargetTonight = null;
        lastSeerCheckResult = null;
        lastSeerCheckTarget = null;
        lastNightDeaths = new ArrayList<>();
        speakDirection = null;
        speakAnchorSeat = 0;
        discussOrder.clear();
        discussIndex = 0;
        dayVotes.clear();
        hunterShooterSeat = null;
        winner = null;
        witchActedThisNight = false;
        seerActedThisNight = false;
        for (PlayerState p : players.values()) {
            p.setAlive(true);
            p.setIdiotRevealed(false);
            p.setCanVote(true);
        }
    }

    public void clearWolfVotesAndLog() {
        wolfKillVotes.clear();
        wolfKillEventLog.clear();
        wolfKillSeq = 0;
    }

    public boolean isWitchActedThisNight() {
        return witchActedThisNight;
    }

    public void setWitchActedThisNight(boolean witchActedThisNight) {
        this.witchActedThisNight = witchActedThisNight;
    }

    public boolean isSeerActedThisNight() {
        return seerActedThisNight;
    }

    public void setSeerActedThisNight(boolean seerActedThisNight) {
        this.seerActedThisNight = seerActedThisNight;
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

    public List<WolfKillEvent> getWolfKillEventLog() {
        return wolfKillEventLog;
    }

    public void appendWolfKillEvent(int wolfId, int targetId) {
        wolfKillSeq++;
        wolfKillEventLog.add(new WolfKillEvent(wolfId, targetId, wolfKillSeq));
    }

    public Integer getPendingWolfKillTarget() {
        return pendingWolfKillTarget;
    }

    public void setPendingWolfKillTarget(Integer pendingWolfKillTarget) {
        this.pendingWolfKillTarget = pendingWolfKillTarget;
    }

    public boolean isWitchAntidoteRemaining() {
        return witchAntidoteRemaining;
    }

    public void setWitchAntidoteRemaining(boolean witchAntidoteRemaining) {
        this.witchAntidoteRemaining = witchAntidoteRemaining;
    }

    public boolean isWitchPoisonRemaining() {
        return witchPoisonRemaining;
    }

    public void setWitchPoisonRemaining(boolean witchPoisonRemaining) {
        this.witchPoisonRemaining = witchPoisonRemaining;
    }

    public Boolean getWitchUsedSaveTonight() {
        return witchUsedSaveTonight;
    }

    public void setWitchUsedSaveTonight(Boolean witchUsedSaveTonight) {
        this.witchUsedSaveTonight = witchUsedSaveTonight;
    }

    public Integer getWitchPoisonTargetTonight() {
        return witchPoisonTargetTonight;
    }

    public void setWitchPoisonTargetTonight(Integer witchPoisonTargetTonight) {
        this.witchPoisonTargetTonight = witchPoisonTargetTonight;
    }

    public Integer getSeerCheckTargetTonight() {
        return seerCheckTargetTonight;
    }

    public void setSeerCheckTargetTonight(Integer seerCheckTargetTonight) {
        this.seerCheckTargetTonight = seerCheckTargetTonight;
    }

    public SeerCheckResult getLastSeerCheckResult() {
        return lastSeerCheckResult;
    }

    public void setLastSeerCheckResult(SeerCheckResult lastSeerCheckResult) {
        this.lastSeerCheckResult = lastSeerCheckResult;
    }

    public Integer getLastSeerCheckTarget() {
        return lastSeerCheckTarget;
    }

    public void setLastSeerCheckTarget(Integer lastSeerCheckTarget) {
        this.lastSeerCheckTarget = lastSeerCheckTarget;
    }

    public List<Integer> getLastNightDeaths() {
        return lastNightDeaths;
    }

    public void setLastNightDeaths(List<Integer> lastNightDeaths) {
        this.lastNightDeaths = lastNightDeaths;
    }

    public SpeakDirection getSpeakDirection() {
        return speakDirection;
    }

    public void setSpeakDirection(SpeakDirection speakDirection) {
        this.speakDirection = speakDirection;
    }

    public int getSpeakAnchorSeat() {
        return speakAnchorSeat;
    }

    public void setSpeakAnchorSeat(int speakAnchorSeat) {
        this.speakAnchorSeat = speakAnchorSeat;
    }

    public List<Integer> getDiscussOrder() {
        return discussOrder;
    }

    public int getDiscussIndex() {
        return discussIndex;
    }

    public void setDiscussIndex(int discussIndex) {
        this.discussIndex = discussIndex;
    }

    public Map<Integer, Integer> getDayVotes() {
        return dayVotes;
    }

    public Integer getHunterShooterSeat() {
        return hunterShooterSeat;
    }

    public void setHunterShooterSeat(Integer hunterShooterSeat) {
        this.hunterShooterSeat = hunterShooterSeat;
    }

    public GameWinner getWinner() {
        return winner;
    }

    public void setWinner(GameWinner winner) {
        this.winner = winner;
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

    public int witchSeat() {
        for (PlayerState p : players.values()) {
            if (p.getRole() == Role.WITCH) {
                return p.getPlayerId();
            }
        }
        return -1;
    }

    public int seerSeat() {
        for (PlayerState p : players.values()) {
            if (p.getRole() == Role.SEER) {
                return p.getPlayerId();
            }
        }
        return -1;
    }

    public void clearWolfKillVotes() {
        wolfKillVotes.clear();
    }

    public void resetWolfNightState() {
        wolfChatInPhase = false;
        wolfKillVotes.clear();
        wolfKillEventLog.clear();
        wolfKillSeq = 0;
        pendingWolfKillTarget = null;
        witchUsedSaveTonight = null;
        witchPoisonTargetTonight = null;
        seerCheckTargetTonight = null;
        witchActedThisNight = false;
        seerActedThisNight = false;
    }

    /** After night settlement — clear witch/seer night flags (death already applied). */
    public void clearNightIntent() {
        witchUsedSaveTonight = null;
        witchPoisonTargetTonight = null;
        seerCheckTargetTonight = null;
        pendingWolfKillTarget = null;
    }
}
