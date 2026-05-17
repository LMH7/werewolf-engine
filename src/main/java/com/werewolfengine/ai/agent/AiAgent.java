package com.werewolfengine.ai.agent;

import com.werewolfengine.ai.prompt.Persona;

/** Per-seat agent identity (ADR-003); memory hooks reserved for Week2+. */
public final class AiAgent {

    private final int seat;
    private final Persona persona;

    public AiAgent(int seat) {
        this.seat = seat;
        this.persona = Persona.forSeat(seat);
    }

    public int seat() {
        return seat;
    }

    public Persona persona() {
        return persona;
    }
}
