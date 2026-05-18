package com.werewolfengine.ai.prompt;

/** PRD §4.5.2 — influences system prompt tone. */
public enum Persona {
    AGGRESSIVE("激进型", "主动带节奏、优先抗推位"),
    CONSERVATIVE("保守型", "谨慎站边、不轻易点人"),
    SARCASTIC("阴阳型", "反问、暗示，少直接点狼"),
    LOGICIAN("逻辑型", "逐条拆发言"),
    EMOTIONAL("情感型", "主观表述、感情牌");

    private final String label;
    private final String hint;

    Persona(String label, String hint) {
        this.label = label;
        this.hint = hint;
    }

    public String label() {
        return label;
    }

    public String hint() {
        return hint;
    }

    public static Persona forSeat(int playerId) {
        return values()[(playerId - 1) % values().length];
    }
}
