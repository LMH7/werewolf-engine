package com.werewolfengine.ai.memory;

import com.werewolfengine.ai.api.AIService;
import com.werewolfengine.ai.config.AiProperties;
import com.werewolfengine.game.engine.GameStateMachine;
import com.werewolfengine.game.model.GamePhase;
import com.werewolfengine.game.model.GameRoomState;
import com.werewolfengine.game.model.Role;
import com.werewolfengine.game.observability.ActionLogService;
import com.werewolfengine.game.orchestration.AiTurnCoordinator;
import com.werewolfengine.game.orchestration.MockGameRunner;
import com.werewolfengine.game.orchestration.TurnActorResolver;
import com.werewolfengine.game.view.SeatPerceptionProjector;
import com.werewolfengine.game.view.SeatPerceptionSlice;
import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Console demo: episodic memory projection + optional live LLM play.
 * Run: {@code mvnw.cmd test -Dtest=MemoryPlaythroughDemoTest#printMemoryAndPlaySample}
 */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.data.redis.autoconfigure.RedisAutoConfiguration"
})
@ActiveProfiles("dev")
class MemoryPlaythroughDemoTest {

    @Autowired
    private GameStateMachine stateMachine;

    @Autowired
    private AIService aiService;

    @Autowired
    private ActionLogService actionLog;

    @Autowired
    private AiProperties aiProperties;

    @Autowired(required = false)
    private ChatModel chatModel;

    @Test
    void printMemoryAndPlaySample() {
        String roomId = "mem_demo_" + System.nanoTime();
        stateMachine.createRoom(roomId);
        stateMachine.markAllReady(roomId);
        assertThat(stateMachine.startGame(roomId).success()).isTrue();

        AiTurnCoordinator coordinator = new AiTurnCoordinator(
                stateMachine,
                new TurnActorResolver(),
                aiService,
                actionLog
        );
        MockGameRunner runner = new MockGameRunner(stateMachine, coordinator);

        System.out.println("\n========== Memory playthrough demo ==========");
        System.out.println("roomId=" + roomId);
        System.out.println("ai.enabled=" + aiProperties.isEnabled()
                + " memory.enabled=" + aiProperties.getMemory().isEnabled()
                + " llm=" + (chatModel != null));

        boolean reachedDay2 = advanceUntil(roomId, coordinator,
                r -> r.getRound() >= 2 && r.getPhase() == GamePhase.DAY_DISCUSS);
        GameRoomState midGame = stateMachine.getRoom(roomId).orElseThrow();
        System.out.println("stopped at round=" + midGame.getRound() + " phase=" + midGame.getPhase()
                + " (target day2 discuss=" + reachedDay2 + ")");

        printActionLogTimeline(actionLog, roomId, 40);

        int wolf = midGame.aliveWolfIds().isEmpty() ? 2 : midGame.aliveWolfIds().getFirst();
        int villager = midGame.alivePlayerIds().stream()
                .filter(id -> midGame.getPlayer(id).getRole() != Role.WEREWOLF)
                .findFirst()
                .orElse(1);

        printSeatMemory(midGame, wolf, "狼座 " + wolf);
        printSeatMemory(midGame, villager, "好人座 " + villager);

        if (chatModel != null && aiProperties.isEnabled() && reachedDay2) {
            printOneLlmTurn(midGame, midGame.getDiscussOrder().get(midGame.getDiscussIndex()));
        } else {
            System.out.println("\n[skip LLM sample] Set DEEPSEEK_API_KEY + werewolf.ai.enabled=true for live decide.");
        }

        if ("true".equalsIgnoreCase(System.getenv("WEREWOLF_DEMO_FULL_GAME"))) {
            System.out.println("\n--- Full game to GAME_OVER (set WEREWOLF_DEMO_FULL_GAME=false to skip) ---");
            var result = runner.runUntilGameOver(roomId, 8_000);
            GameRoomState endGame = stateMachine.getRoom(roomId).orElseThrow();
            System.out.println("outcome=" + result.outcome() + " steps=" + result.steps()
                    + " winner=" + result.winner() + " phase=" + endGame.getPhase());
            printActionLogTimeline(actionLog, roomId, 25);
            printSeatMemory(endGame, wolf, "终局后 · 狼座 " + wolf + " 记忆（尾部）");
        } else {
            System.out.println("\n[tip] HTTP: POST /internal/game/rooms/{id}/mock-auto-play for full LLM game.");
            System.out.println("      Or: WEREWOLF_DEMO_FULL_GAME=true mvnw test -Dtest=MemoryPlaythroughDemoTest");
        }
        System.out.println("========== end demo ==========\n");
    }

    private void printOneLlmTurn(GameRoomState room, int seat) {
        System.out.println("\n--- Live LLM decide · seat " + seat + " phase=" + room.getPhase() + " ---");
        try {
            var intent = aiService.decide(room, seat);
            System.out.println("intent=" + intent.map(i -> i.action() + " target=" + i.target()
                    + " reason=" + i.reason()).orElse("(empty)"));
        } catch (Exception e) {
            System.out.println("decide failed: " + e);
        }
    }

    private void printSeatMemory(GameRoomState room, int seat, String label) {
        var log = actionLog.getLog(room.getRoomId());
        SeatPerceptionSlice slice = SeatPerceptionProjector.project(room, seat, log);
        var opts = new MemoryPromptFormatter.FormatOptions(
                aiProperties.getMemory().getMaxEvents(),
                aiProperties.getMemory().getMaxChars(),
                aiProperties.getMemory().isIncludeOwnThinking()
        );
        var formatted = new MemoryPromptFormatter().format(slice, opts, true);
        System.out.println("\n--- " + label + " · events=" + slice.events().size()
                + " truncated=" + formatted.truncated() + " ---");
        String text = formatted.text();
        if (text.length() > 1200) {
            System.out.println(text.substring(0, 1200) + "\n... (" + text.length() + " chars total)");
        } else {
            System.out.println(text.isBlank() ? "(empty memory)" : text);
        }
    }

    private static void printActionLogTimeline(ActionLogService actionLog, String roomId, int tailLines) {
        var entries = actionLog.getLog(roomId);
        int from = Math.max(0, entries.size() - tailLines);
        System.out.println("\n--- action_log (last " + (entries.size() - from) + " / " + entries.size() + ") ---");
        var last = (Object) null;
        for (int i = from; i < entries.size(); i++) {
            var e = entries.get(i);
            var key = e.round() + "|" + e.phase();
            if (!key.equals(last)) {
                System.out.println("\n  [R" + e.round() + " " + e.phase() + "]");
                last = key;
            }
            String who = e.playerId() == 0 ? "SYS" : "P" + e.playerId();
            String act = e.action() != null ? e.action().name() : "-";
            String tgt = e.target() != null ? " ->" + e.target() : "";
            String line = e.content() != null ? e.content() : e.thinking();
            String extra = line != null && !line.isBlank()
                    ? " | " + (line.length() > 60 ? line.substring(0, 60) + "…" : line)
                    : "";
            System.out.printf("    %3d %s %-12s%s%s%n", i + 1, who, act, tgt, extra);
        }
    }

    private boolean advanceUntil(
            String roomId,
            AiTurnCoordinator coordinator,
            Predicate<GameRoomState> done
    ) {
        for (int i = 0; i < MockGameRunner.DEFAULT_MAX_STEPS; i++) {
            GameRoomState room = stateMachine.getRoom(roomId).orElseThrow();
            if (done.test(room) || room.getPhase() == GamePhase.GAME_OVER) {
                return done.test(room);
            }
            if (!coordinator.tickOneStep(roomId, room)) {
                return false;
            }
        }
        return false;
    }
}
