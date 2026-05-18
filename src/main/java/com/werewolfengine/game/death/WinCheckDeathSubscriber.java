package com.werewolfengine.game.death;

import com.werewolfengine.game.observability.ActionLogService;
import com.werewolfengine.game.win.GameOutcome;
import com.werewolfengine.game.model.GameRoomState;
import com.werewolfengine.game.model.GameWinner;

import java.util.List;
import java.util.Optional;

final class WinCheckDeathSubscriber implements DeathSubscriber {

    private final ActionLogService actionLog;

    WinCheckDeathSubscriber() {
        this(null);
    }

    WinCheckDeathSubscriber(ActionLogService actionLog) {
        this.actionLog = actionLog;
    }

    @Override
    public Optional<GameWinner> afterDeaths(GameRoomState room, List<DeathRecord> records) {
        return GameOutcome.evaluateAndEndIfMet(room, actionLog);
    }
}
