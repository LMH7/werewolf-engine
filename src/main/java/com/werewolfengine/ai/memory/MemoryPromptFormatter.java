package com.werewolfengine.ai.memory;

import com.werewolfengine.game.view.PerceptionEventKind;
import com.werewolfengine.game.view.SeatPerceptionSlice;
import com.werewolfengine.game.view.VisibleEvent;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Formats {@link SeatPerceptionSlice} into the Prompt memory block (ADR-004 §7).
 */
@Component
public class MemoryPromptFormatter {

    public static final int DEFAULT_MAX_EVENTS = 30;
    public static final int DEFAULT_MAX_CHARS = 2000;
    public static final int DEFAULT_LINE_CONTENT_MAX = 120;

    public record FormatOptions(
            int maxEvents,
            int maxChars,
            boolean includeOwnThinking
    ) {
        public FormatOptions() {
            this(DEFAULT_MAX_EVENTS, DEFAULT_MAX_CHARS, false);
        }
    }

    public record FormatResult(String text, boolean truncated) {
    }

    public String format(SeatPerceptionSlice slice) {
        return format(slice, new FormatOptions(), true).text();
    }

    public FormatResult format(SeatPerceptionSlice slice, FormatOptions options, boolean prefixHeader) {
        if (slice == null || slice.events().isEmpty()) {
            return new FormatResult("", false);
        }
        List<VisibleEvent> events = new ArrayList<>(slice.events());
        boolean truncatedByCount = events.size() > options.maxEvents();
        if (truncatedByCount) {
            events = events.subList(events.size() - options.maxEvents(), events.size());
        }
        StringBuilder sb = new StringBuilder();
        if (prefixHeader) {
            sb.append("## 本局记忆（仅你可见）\n");
        }
        if (truncatedByCount) {
            sb.append("- （更早事件已省略，详见当前局面）\n");
        }
        boolean truncatedByChars = false;
        for (VisibleEvent e : events) {
            if (e.kind() == PerceptionEventKind.SELF_THINKING && !options.includeOwnThinking()) {
                continue;
            }
            String line = formatLine(e);
            if (sb.length() + line.length() + 1 > options.maxChars()) {
                truncatedByChars = true;
                break;
            }
            sb.append("- ").append(line).append('\n');
        }
        return new FormatResult(sb.toString().trim(), truncatedByCount || truncatedByChars);
    }

    private static String formatLine(VisibleEvent e) {
        String phase = e.phase() != null ? e.phase().name() : "?";
        return "[R" + e.round() + " " + phase + "] " + e.summary();
    }
}
