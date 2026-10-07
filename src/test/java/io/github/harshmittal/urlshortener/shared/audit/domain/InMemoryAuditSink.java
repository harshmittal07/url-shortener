package io.github.harshmittal.urlshortener.shared.audit.domain;

import java.util.ArrayList;
import java.util.List;

public final class InMemoryAuditSink implements AuditSink {

    private final List<AuditEvent> events = new ArrayList<>();

    @Override
    public void append(AuditEvent event) {
        events.add(event);
    }

    public List<AuditEvent> events() {
        return List.copyOf(events);
    }
}
