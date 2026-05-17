package com.werewolfengine.game.exile;

import com.werewolfengine.game.GameOutcome;
import com.werewolfengine.game.GameStateMachine;
import com.werewolfengine.game.WinChecker;
import com.werewolfengine.game.death.DeathBus;
import com.werewolfengine.game.death.DeathCause;
import com.werewolfengine.game.death.DeathRecord;
import com.werewolfengine.game.model.ActionAck;
import com.werewolfengine.game.model.GamePhase;
import com.werewolfengine.game.model.GameRoomState;
import com.werewolfengine.game.model.PlayerState;
import com.werewolfengine.game.model.Role;

import java.util.List;
import java.util.function.Function;

/**
 * Day vote exile resolution (idiot reveal, hunter pending, death bus for kills).
 */
public final class ExileResolver {

    private final DeathBus deathBus;

    public ExileResolver() {
        this(new DeathBus());
    }

    public ExileResolver(DeathBus deathBus) {
        this.deathBus = deathBus;
    }

    public GameStateMachine.HandleActionResult advanceAfterVote(
            GameRoomState room,
            ActionAck priorAck,
            Integer exileSeat,
            Function<GameRoomState, GameStateMachine.HandleActionResult> continueAfterVote
    ) {
        room.setPhase(GamePhase.VOTE_RESULT);
        room.getDayVotes().clear();

        if (exileSeat != null) {
            PlayerState ex = room.getPlayer(exileSeat);
            if (ex != null && ex.isAlive()) {
                if (ex.getRole() == Role.IDIOT && !ex.isIdiotRevealed()) {
                    ex.setIdiotRevealed(true);
                    ex.setCanVote(false);
                    return continueAfterVote.apply(room);
                }
                if (ex.getRole() == Role.HUNTER) {
                    if (WinChecker.isOnlyLivingGod(room, exileSeat)) {
                        var result = deathBus.apply(room,
                                List.of(new DeathRecord(exileSeat, DeathCause.VOTE_EXILE)));
                        if (result.gameEnded()) {
                            return GameStateMachine.HandleActionResult.of(
                                    ActionAck.ok("最后一神被放逐，狼人获胜", GamePhase.VOTE_RESULT, null),
                                    GameOutcome.syncsAllAlive(room));
                        }
                    }
                    room.setExileAnnouncedSeat(exileSeat);
                    room.setPendingHunterAfterAnnounce(exileSeat);
                    room.setPhase(GamePhase.EXILE_DEATH_ANNOUNCE);
                    return GameStateMachine.HandleActionResult.of(priorAck, GameOutcome.syncsAllAlive(room));
                }
                var result = deathBus.apply(room, List.of(new DeathRecord(exileSeat, DeathCause.VOTE_EXILE)));
                if (result.gameEnded()) {
                    return GameStateMachine.HandleActionResult.of(priorAck, GameOutcome.syncsAllAlive(room));
                }
                room.setExileAnnouncedSeat(exileSeat);
                room.setPhase(GamePhase.EXILE_DEATH_ANNOUNCE);
                return GameStateMachine.HandleActionResult.of(priorAck, GameOutcome.syncsAllAlive(room));
            }
        }

        return continueAfterVote.apply(room);
    }
}
