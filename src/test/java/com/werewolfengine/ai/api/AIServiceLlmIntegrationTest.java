package com.werewolfengine.ai.api;

import com.werewolfengine.game.engine.GameStateMachine;
import com.werewolfengine.game.model.GameActionType;
import com.werewolfengine.game.model.GamePhase;
import com.werewolfengine.game.model.GameRoomState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Live DeepSeek call; skipped when {@code DEEPSEEK_API_KEY} is unset.
 */
@SpringBootTest(properties = {
        "werewolf.ai.enabled=true",
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.data.redis.autoconfigure.RedisAutoConfiguration"
})
@ActiveProfiles("dev")
@EnabledIfEnvironmentVariable(named = "DEEPSEEK_API_KEY", matches = ".+")
class AIServiceLlmIntegrationTest {

    @Autowired
    private AIService aiService;

    @Autowired
    private GameStateMachine stateMachine;

    @Test
    void deepSeekReturnsLegalWolfKill() {
        String roomId = "llm_it_" + System.nanoTime();
        stateMachine.createRoom(roomId);
        stateMachine.markAllReady(roomId);
        assertThat(stateMachine.startGame(roomId).success()).isTrue();

        GameRoomState room = stateMachine.getRoom(roomId).orElseThrow();
        assertThat(room.getPhase()).isEqualTo(GamePhase.NIGHT_WOLF);

        int wolf = room.aliveWolfIds().getFirst();
        Optional<PlayerIntent> intent = aiService.decide(room, wolf);

        assertThat(intent)
                .as("LLM or mock fallback must yield a wolf kill intent")
                .isPresent();
        assertThat(intent.get().action()).isIn(GameActionType.KILL, GameActionType.WOLF_CHAT);
        if (intent.get().action() == GameActionType.KILL) {
            assertThat(intent.get().target()).isBetween(1, 12);
            assertThat(room.aliveWolfIds()).doesNotContain(intent.get().target());
        }
        assertThat(intent.get().reason())
                .as("expect LLM path (not MockAIPlayer 'mock ...' reason)")
                .doesNotStartWith("mock");
    }

    @Test
    void wolfNightLlmDemoIntent() {
        String roomId = "llm_wolf_demo_" + System.nanoTime();
        stateMachine.createRoom(roomId);
        stateMachine.markAllReady(roomId);
        assertThat(stateMachine.startGame(roomId).success()).isTrue();

        GameRoomState room = stateMachine.getRoom(roomId).orElseThrow();
        assertThat(room.getPhase()).isEqualTo(GamePhase.NIGHT_WOLF);

        int successes = 0;
        for (int wolfId : room.aliveWolfIds()) {
            Optional<PlayerIntent> intent = aiService.decide(room, wolfId);
            if (intent.isPresent()
                    && (intent.get().action() == GameActionType.WOLF_CHAT
                    || intent.get().action() == GameActionType.KILL)) {
                successes++;
            }
        }
        assertThat(successes)
                .as("at least one wolf should produce WOLF_CHAT or KILL")
                .isGreaterThanOrEqualTo(1);
    }
}
