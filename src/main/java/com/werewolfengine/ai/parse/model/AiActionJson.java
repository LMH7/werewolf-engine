package com.werewolfengine.ai.parse.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** PRD §4.5.3 frozen JSON schema (LLM output). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AiActionJson(
        String thinking,
        String action,
        Integer target,
        String reason,
        String content
) {
}
