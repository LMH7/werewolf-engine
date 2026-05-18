package com.werewolfengine.ai.memory;

import com.werewolfengine.game.model.GamePhase;
import com.werewolfengine.game.view.PerceptionEventKind;
import com.werewolfengine.game.view.SeatPerceptionSlice;
import com.werewolfengine.game.view.VisibleEvent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryPromptFormatterTest {

    @Test
    void m3e_truncatesToLast30EventsAndMaxChars() {
        List<VisibleEvent> events = new ArrayList<>();
        for (int i = 1; i <= 35; i++) {
            events.add(new VisibleEvent(
                    1,
                    GamePhase.DAY_DISCUSS,
                    PerceptionEventKind.PUBLIC_SPEAK,
                    i,
                    null,
                    "座位" + i + " 发言：" + "x".repeat(80)
            ));
        }
        MemoryPromptFormatter formatter = new MemoryPromptFormatter();
        var opts = new MemoryPromptFormatter.FormatOptions(30, 2000, false);
        var result = formatter.format(new SeatPerceptionSlice(events), opts, true);

        assertThat(result.truncated()).isTrue();
        assertThat(result.text()).contains("更早事件已省略");
        long lineCount = result.text().lines().filter(l -> l.startsWith("- [R")).count();
        assertThat(lineCount).isLessThanOrEqualTo(30);
        assertThat(result.text().length()).isLessThanOrEqualTo(2100);
    }
}
