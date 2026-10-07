package io.github.harshmittal.urlshortener.shared.audit.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.harshmittal.urlshortener.shared.tx.domain.InlineUnitOfWork;
import io.github.harshmittal.urlshortener.shared.tx.domain.UnitOfWork;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AuditTrailTest {

    private static final Instant NOW = Instant.parse("2026-10-07T10:15:30.123456789Z");
    private static final UUID EVENT_ID = UUID.fromString("00000000-0000-4000-8000-0000000000e1");
    private static final AuditContext CONTEXT = new AuditContext("req-1", UUID.randomUUID(), "c".repeat(64));

    private final InMemoryAuditSink sink = new InMemoryAuditSink();
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    @DisplayName("S-10: a state change is recorded with the injected clock's time and outcome SUCCESS")
    void recordsChange() {
        new AuditTrail(sink, new InlineUnitOfWork(), clock, () -> EVENT_ID)
                .recordChange(CONTEXT, AuditAction.LINK_CREATED, "LINK", "aB3dE5g");

        assertThat(sink.events())
                .containsExactly(new AuditEvent(
                        EVENT_ID,
                        Instant.parse("2026-10-07T10:15:30.123456Z"),
                        "req-1",
                        CONTEXT.actorKeyId(),
                        AuditAction.LINK_CREATED,
                        "LINK",
                        "aB3dE5g",
                        Outcome.SUCCESS,
                        null,
                        CONTEXT.clientIpHash()));
    }

    @Test
    @DisplayName("R18: a failed write during a state change propagates so the change rolls back")
    void changeFailurePropagates() {
        var trail = new AuditTrail(new FailingAuditSink(), new InlineUnitOfWork(), clock, UUID::randomUUID);

        assertThatThrownBy(() -> trail.recordChange(CONTEXT, AuditAction.LINK_CREATED, "LINK", "aB3dE5g"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("R20: a rejection is recorded in its own transaction with outcome REJECTED and a reason")
    void recordsRejectionInNewTransaction() {
        var requiresNewCalls = new AtomicInteger();
        UnitOfWork countingUnitOfWork = new UnitOfWork() {
            @Override
            public <T> T inTransaction(Supplier<T> work) {
                return work.get();
            }

            @Override
            public void requiresNew(Runnable work) {
                requiresNewCalls.incrementAndGet();
                work.run();
            }
        };

        new AuditTrail(sink, countingUnitOfWork, clock, () -> EVENT_ID)
                .recordRejection(CONTEXT, AuditAction.URL_REJECTED, "LINK", null, "SCHEME_NOT_ALLOWED");

        assertThat(requiresNewCalls).hasValue(1);
        assertThat(sink.events()).singleElement().satisfies(event -> {
            assertThat(event.outcome()).isEqualTo(Outcome.REJECTED);
            assertThat(event.reasonCode()).isEqualTo("SCHEME_NOT_ALLOWED");
        });
    }

    @Test
    @DisplayName("R20: a failed rejection write is swallowed, so the rejection response is unchanged")
    void rejectionFailureIsSwallowed() {
        var trail = new AuditTrail(new FailingAuditSink(), new InlineUnitOfWork(), clock, UUID::randomUUID);

        assertThatCode(() -> trail.recordRejection(CONTEXT, AuditAction.AUTH_FAILED, null, null, "UNKNOWN"))
                .doesNotThrowAnyException();
    }
}
