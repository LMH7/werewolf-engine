package com.werewolfengine.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code werewolf.ai.*} — LLM 开关与 MVP 分阶段（§4.5.5 P0.5 仅狼人）。
 */
@ConfigurationProperties(prefix = "werewolf.ai")
public class AiProperties {

    /**
     * When false, {@link com.werewolfengine.ai.api.AIService} uses {@link com.werewolfengine.ai.policy.MockAIPlayer} only.
     */
    private boolean enabled = false;

    /**
     * P0.5: only werewolves in {@code NIGHT_WOLF} call LLM; other seats use Mock fallback rules.
     */
    private boolean wolvesOnly = false;

    /** Recorded on action_log lines when LLM is used (PRD §4.7.3). */
    private String modelId = "deepseek-v4-flash";

    private final Memory memory = new Memory();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isWolvesOnly() {
        return wolvesOnly;
    }

    public void setWolvesOnly(boolean wolvesOnly) {
        this.wolvesOnly = wolvesOnly;
    }

    public String getModelId() {
        return modelId;
    }

    public void setModelId(String modelId) {
        this.modelId = modelId;
    }

    public Memory getMemory() {
        return memory;
    }

    public static class Memory {
        private boolean enabled = true;
        private boolean includeOwnThinking = false;
        private int maxEvents = 30;
        private int maxChars = 2000;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isIncludeOwnThinking() {
            return includeOwnThinking;
        }

        public void setIncludeOwnThinking(boolean includeOwnThinking) {
            this.includeOwnThinking = includeOwnThinking;
        }

        public int getMaxEvents() {
            return maxEvents;
        }

        public void setMaxEvents(int maxEvents) {
            this.maxEvents = maxEvents;
        }

        public int getMaxChars() {
            return maxChars;
        }

        public void setMaxChars(int maxChars) {
            this.maxChars = maxChars;
        }
    }
}
