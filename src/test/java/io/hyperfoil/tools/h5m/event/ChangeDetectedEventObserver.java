package io.hyperfoil.tools.h5m.event;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class ChangeDetectedEventObserver {

    private final List<ChangeDetectedEvent> events = new ArrayList<>();

    void onChangeDetected(@Observes ChangeDetectedEvent event) {
        events.add(event);
    }

    public List<ChangeDetectedEvent> getEvents() {
        return events;
    }

    public void clear() {
        events.clear();
    }
}
