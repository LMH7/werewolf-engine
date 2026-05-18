package com.werewolfengine.ai.tools;

import com.werewolfengine.ai.guard.AiLegalActions;
import com.werewolfengine.ai.perceive.GameViewContext;
import com.werewolfengine.game.engine.GameStateMachine;
import com.werewolfengine.game.model.GameActionType;
import com.werewolfengine.game.model.GameRoomState;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * LangChain4j tools for Week2+ agents (read-only; PRD 4.5.1, ADR-003 Phase B).
 */
@Component
public class GameTools {

    private final GameStateMachine stateMachine;
    private final AiLegalActions legalActions;

    public GameTools(GameStateMachine stateMachine, AiLegalActions legalActions) {
        this.stateMachine = stateMachine;
        this.legalActions = legalActions;
    }

    @Tool("\u8fd4\u56de\u6307\u5b9a\u5ea7\u4f4d\u5f53\u524d\u53ef\u89c1\u5c40\u51b5\uff08\u9636\u6bb5\u3001\u5b58\u6d3b\u3001\u89d2\u8272\u79c1\u5bc6\u5b57\u6bb5\u7b49\uff09")
    public String describeGameView(String roomId, int seat) {
        GameRoomState room = stateMachine.getRoom(roomId)
                .orElseThrow(() -> new IllegalArgumentException("Room not found: " + roomId));
        return GameViewContext.forSeat(room, seat).toString();
    }

    @Tool("\u8fd4\u56de\u6307\u5b9a\u5ea7\u4f4d\u672c\u56de\u5408\u5408\u6cd5 action \u5217\u8868")
    public String listLegalActions(String roomId, int seat) {
        GameRoomState room = stateMachine.getRoom(roomId)
                .orElseThrow(() -> new IllegalArgumentException("Room not found: " + roomId));
        Set<GameActionType> allowed = legalActions.allowed(room, seat);
        return legalActions.formatAllowedList(allowed);
    }
}
