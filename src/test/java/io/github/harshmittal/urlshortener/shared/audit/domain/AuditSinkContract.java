package io.github.harshmittal.urlshortener.shared.audit.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Contract for every {@link AuditSink} adapter. */
public abstract class AuditSinkContract {

    protected abstract AuditSink sink();

    protected abstract Optional<AuditEvent> stored(UUID id);

    @Test
    @DisplayName("S-10: an appended event is stored with every field")
    void storesEveryField() {
        AuditEvent event = new AuditEvent(
                UUID.randomUUID(),
                Instant.parse("2026-10-07T10:15:30.123456Z"),
                "req-contract",
                UUID.randomUUID(),
                AuditAction.LINK_CREATED,
                "LINK",
                "aB3dE5g",
                Outcome.SUCCESS,
                null,
                "a".repeat(64));

        sink().append(event);

        assertThat(stored(event.id())).contains(event);
    }

    @Test
    @DisplayName("S-10: an unauthenticated rejection is stored with no actor and no resource")
    void storesEventWithNullableFieldsEmpty() {
        AuditEvent event = new AuditEvent(
                UUID.randomUUID(),
                Instant.parse("2026-10-07T10:15:30Z"),
                "req-contract-anon",
                null,
                AuditAction.AUTH_FAILED,
                null,
                null,
                Outcome.REJECTED,
                "MISSING",
                "b".repeat(64));

        sink().append(event);

        assertThat(stored(event.id())).contains(event);
    }
}
