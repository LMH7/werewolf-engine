package com.werewolfengine.ai.parse;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PRD §1.2 P1 — 50 次 LLM 风格 JSON 解析成功率 &gt; 95% (ADR-003 §7 P1).
 */
class AiJsonParseSuccessRateTest {

    private static final List<String> SAMPLES = List.of(
            "{\"thinking\":\"t\",\"action\":\"KILL\",\"target\":3,\"reason\":\"r\"}",
            "{\"action\":\"WOLF_CHAT\",\"content\":\"今晚刀8\",\"reason\":\"商议\"}",
            "{\"action\":\"SKIP\",\"reason\":\"不救\"}",
            "```json\n{\"action\":\"CHECK\",\"target\":5,\"reason\":\"查\"}\n```",
            "Result: {\"action\":\"VOTE\",\"target\":2,\"reason\":\"投\"}",
            "{\"thinking\":\"x\",\"action\":\"SPEAK\",\"content\":\"过\",\"reason\":\"说\"}",
            "{\"action\":\"SKIP_SPEAK\",\"reason\":\"过\"}",
            "{\"action\":\"SKIP_VOTE\",\"reason\":\"弃\"}",
            "{\"action\":\"SAVE\",\"target\":8,\"reason\":\"救\"}",
            "{\"action\":\"POISON\",\"target\":4,\"reason\":\"毒\"}",
            "{\"action\":\"SHOOT\",\"target\":6,\"reason\":\"枪\"}"
    );

    private final AiIntentParser parser = new AiIntentParser();

    @Test
    void atLeastNinetyFivePercentOverFiftyDraws() {
        int ok = 0;
        for (int i = 0; i < 50; i++) {
            String raw = SAMPLES.get(i % SAMPLES.size());
            try {
                parser.parse(raw);
                ok++;
            } catch (RuntimeException ignored) {
                // count failure
            }
        }
        assertThat(ok).as("parse success count out of 50").isGreaterThanOrEqualTo(48);
    }
}
