package io.github.harshmittal.urlshortener.shared.audit.domain;

import java.util.Optional;
import java.util.UUID;

class InMemoryAuditSinkTest extends AuditSinkContract {

    private final InMemoryAuditSink sink = new InMemoryAuditSink();

    @Override
    protected AuditSink sink() {
        return sink;
    }

    @Override
    protected Optional<AuditEvent> stored(UUID id) {
        return sink.events().stream().filter(event -> event.id().equals(id)).findFirst();
    }
}
