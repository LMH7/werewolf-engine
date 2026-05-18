package com.werewolfengine.game.view;

import java.util.List;

/** Episodic events visible to one seat (ADR-004). */
public record SeatPerceptionSlice(List<VisibleEvent> events) {

    public SeatPerceptionSlice {
        events = events == null ? List.of() : List.copyOf(events);
    }
}
