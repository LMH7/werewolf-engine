package com.werewolfengine.game.night;

import com.werewolfengine.game.engine.GameStateMachine;
import com.werewolfengine.game.death.DeathBus;
import com.werewolfengine.game.model.GameActionCommand;
import com.werewolfengine.game.model.GameRoomState;
import com.werewolfengine.game.model.PlayerState;
import com.werewolfengine.message.payload.PhaseSyncPayload;

import java.util.List;

/**
 * Ordered night skill handlers: wolf → seer → witch (PRD §4.3.8.4, ADR-001).
 */
public final class NightSkillPipeline {

    private final NightActions nightActions;

    public NightSkillPipeline() {
        this(new NightActions(new DeathBus()));
    }

    public NightSkillPipeline(NightActions nightActions) {
        this.nightActions = nightActions;
    }

    public NightActions nightActions() {
        return nightActions;
    }

    public GameStateMachine.HandleActionResult handleAction(
            GameRoomState room,
            PlayerState actor,
            GameActionCommand command
    ) {
        return nightActions.handle(room, actor, command);
    }

    public void enterNightWolf(GameRoomState room) {
        nightActions.enterNightWolf(room);
    }

    public List<PhaseSyncPayload> buildWolfPhaseSyncs(GameRoomState room) {
        return nightActions.buildWolfPhaseSyncs(room);
    }
}
