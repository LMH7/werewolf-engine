package com.werewolfengine.game.model;

public final class PlayerState {

    private final int playerId;
    private Role role;
    private boolean alive;
    private boolean ready;

    public PlayerState(int playerId) {
        this.playerId = playerId;
        this.alive = true;
        this.ready = false;
    }

    public int getPlayerId() {
        return playerId;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public boolean isAlive() {
        return alive;
    }

    public void setAlive(boolean alive) {
        this.alive = alive;
    }

    public boolean isReady() {
        return ready;
    }

    public void setReady(boolean ready) {
        this.ready = ready;
    }
}
