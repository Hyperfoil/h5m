package io.hyperfoil.tools.h5m.event;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class ChangeEventObserver {

    private final List<ChangeEvent> events = new ArrayList<>();

    void onChangeDetected(@Observes ChangeEvent event) {
        events.add(event);
    }

    public List<ChangeEvent> getEvents() {
        return events;
    }

    public void clear() {
        events.clear();
    }
}
