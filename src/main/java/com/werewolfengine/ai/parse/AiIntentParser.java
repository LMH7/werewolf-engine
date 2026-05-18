package com.werewolfengine.ai.parse;

import com.werewolfengine.ai.api.PlayerIntent;
import com.werewolfengine.ai.parse.model.AiActionJson;
import com.werewolfengine.game.model.GameActionType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class AiIntentParser {

    private static final Pattern JSON_BLOCK = Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);
    private static final int REASON_MAX = 30;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public PlayerIntent parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("empty LLM response");
        }
        String json = extractJson(raw.trim());
        AiActionJson dto;
        try {
            dto = jsonMapper.readValue(json, AiActionJson.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("invalid JSON: " + e.getMessage(), e);
        }
        if (dto.action() == null || dto.action().isBlank()) {
            throw new IllegalArgumentException("missing action");
        }
        GameActionType type;
        try {
            type = GameActionType.valueOf(dto.action().trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("unknown action: " + dto.action());
        }
        String reason = truncate(dto.reason(), REASON_MAX);
        if (reason == null || reason.isBlank()) {
            reason = truncate(dto.thinking(), REASON_MAX);
        }
        if (reason == null || reason.isBlank()) {
            reason = "llm";
        }
        return new PlayerIntent(type, dto.target(), reason, dto.content(), dto.thinking());
    }

    static String extractJson(String text) {
        Matcher block = JSON_BLOCK.matcher(text);
        if (block.find()) {
            return block.group(1).trim();
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        if (t.length() <= max) {
            return t;
        }
        return t.substring(0, max);
    }
}
