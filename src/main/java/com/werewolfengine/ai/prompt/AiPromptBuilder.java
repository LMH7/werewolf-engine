package com.werewolfengine.ai.prompt;

import com.werewolfengine.ai.perceive.GameViewContext;
import com.werewolfengine.game.model.GameActionType;
import com.werewolfengine.game.model.GamePhase;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class AiPromptBuilder {

    public String systemMessage(Persona persona) {
        return """
                你是 12 人狼人杀（预女猎愚+愚者）中的 AI 玩家。只根据当局可见信息决策。
                性格：%s（%s）。
                必须只输出一行 JSON，不要 markdown，不要解释。字段：
                {"thinking":"最多100字","action":"...","target":座位号或省略,"reason":"最多30字","content":"可选"}
                action 只能是：KILL, WOLF_CHAT, SAVE, POISON, CHECK, SPEAK, VOTE, SHOOT, SKIP, SKIP_SPEAK, SKIP_VOTE。
                刀狼队友或自刀前须先 WOLF_CHAT；女巫同夜不能先救后毒。
                """.formatted(persona.label(), persona.hint());
    }

    public String userMessage(GameViewContext view, Set<GameActionType> allowed) {
        return userMessage(view, allowed, null);
    }

    public String userMessage(GameViewContext view, Set<GameActionType> allowed, String memoryBlock) {
        StringBuilder sb = new StringBuilder();
        if (memoryBlock != null && !memoryBlock.isBlank()) {
            sb.append(memoryBlock.trim()).append("\n\n");
            sb.append("## 当前局面\n");
        }
        sb.append("你的座位: ").append(view.seat()).append('\n');
        sb.append("你的角色: ").append(view.yourRole()).append('\n');
        sb.append("阶段: ").append(view.phase()).append(" 轮次: ").append(view.round()).append('\n');
        sb.append("存活座位: ").append(view.aliveSeats()).append('\n');
        if (view.wolfTeammates() != null && !view.wolfTeammates().isEmpty()) {
            sb.append("狼队友座位: ").append(view.wolfTeammates()).append('\n');
        }
        if (view.pendingWolfKill() != null) {
            sb.append("本夜刀口座位: ").append(view.pendingWolfKill()).append('\n');
        }
        if (view.phase() == GamePhase.NIGHT_WITCH) {
            sb.append("解药剩余: ").append(view.witchAntidoteLeft())
                    .append(" 毒药剩余: ").append(view.witchPoisonLeft()).append('\n');
        }
        if (view.lastSeerResult() != null) {
            sb.append("你上次查验: 座位").append(view.lastSeerTarget())
                    .append(" 结果=").append(view.lastSeerResult()).append('\n');
        }
        if (view.phase() == GamePhase.NIGHT_WOLF) {
            sb.append("本夜是否已狼聊: ").append(view.wolfChatDone()).append('\n');
        }
        if (view.currentSpeaker() != null) {
            sb.append("当前发言/遗言座位: ").append(view.currentSpeaker()).append('\n');
        }
        sb.append("本回合合法 action（必须从中选一个）: ");
        boolean first = true;
        for (GameActionType a : allowed) {
            if (!first) {
                sb.append(", ");
            }
            sb.append(a.name());
            first = false;
        }
        sb.append('\n');
        sb.append("请输出 JSON。");
        return sb.toString();
    }
}
