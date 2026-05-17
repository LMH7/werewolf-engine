package com.werewolfengine.ai.parse;

import com.werewolfengine.ai.api.PlayerIntent;
import com.werewolfengine.game.model.GameActionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiIntentParserTest {

    private AiIntentParser parser;

    @BeforeEach
    void setUp() {
        parser = new AiIntentParser();
    }

    @Test
    void parsesPlainJson() {
        PlayerIntent intent = parser.parse("""
                {"thinking":"刀村民","action":"KILL","target":5,"reason":"随机刀"}
                """);
        assertThat(intent.action()).isEqualTo(GameActionType.KILL);
        assertThat(intent.target()).isEqualTo(5);
        assertThat(intent.reason()).isEqualTo("随机刀");
    }

    @Test
    void parsesContentAndThinking() {
        PlayerIntent intent = parser.parse("""
                {"thinking":"内部分析","action":"SPEAK","reason":"发言","content":"大家好"}
                """);
        assertThat(intent.action()).isEqualTo(GameActionType.SPEAK);
        assertThat(intent.content()).isEqualTo("大家好");
        assertThat(intent.thinking()).isEqualTo("内部分析");
    }

    @Test
    void parsesMarkdownFencedJson() {
        PlayerIntent intent = parser.parse("""
                ```json
                {"action":"SKIP","reason":"不救"}
                ```
                """);
        assertThat(intent.action()).isEqualTo(GameActionType.SKIP);
    }

    @Test
    void rejectsUnknownAction() {
        assertThatThrownBy(() -> parser.parse("{\"action\":\"FLY\"}"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void extractJsonFromProse() {
        String json = AiIntentParser.extractJson("Here: {\"action\":\"VOTE\",\"target\":3}");
        assertThat(json).contains("\"action\":\"VOTE\"");
    }
}
