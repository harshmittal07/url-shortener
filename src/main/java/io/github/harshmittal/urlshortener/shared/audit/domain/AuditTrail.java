package io.github.harshmittal.urlshortener.shared.audit.domain;

import io.github.harshmittal.urlshortener.shared.id.domain.IdGenerator;
import io.github.harshmittal.urlshortener.shared.tx.domain.UnitOfWork;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Records audit events: state changes in the caller's transaction, rejections in their own. */
public final class AuditTrail {

    private static final Logger log = LoggerFactory.getLogger(AuditTrail.class);

    private final AuditSink sink;
    private final UnitOfWork unitOfWork;
    private final Clock clock;
    private final IdGenerator ids;

    public AuditTrail(AuditSink sink, UnitOfWork unitOfWork, Clock clock, IdGenerator ids) {
        this.sink = sink;
        this.unitOfWork = unitOfWork;
        this.clock = clock;
        this.ids = ids;
    }

    /**
     * Records a state change. Call inside the use case's transaction: if this write fails, the
     * exception propagates and the change rolls back (R18).
     */
    public void recordChange(AuditContext context, AuditAction action, String resourceType, String resourceId) {
        sink.append(event(context, action, resourceType, resourceId, Outcome.SUCCESS, null));
    }

    /**
     * Records a rejection in its own transaction, so an enclosing rollback cannot erase it. Best
     * effort: a failed write is logged and never changes the rejection response (R20).
     */
    public void recordRejection(
            AuditContext context, AuditAction action, String resourceType, String resourceId, String reasonCode) {
        AuditEvent event = event(context, action, resourceType, resourceId, Outcome.REJECTED, reasonCode);
        try {
            unitOfWork.requiresNew(() -> sink.append(event));
        } catch (RuntimeException e) {
            // Any failure is caught on purpose: a rejection must never fail open or change shape.
            log.warn(
                    "Audit write failed: action={} requestId={} actorKeyId={} cause={}",
                    action,
                    context.requestId(),
                    context.actorKeyId(),
                    e.getClass().getSimpleName());
        }
    }

    private AuditEvent event(
            AuditContext context,
            AuditAction action,
            String resourceType,
            String resourceId,
            Outcome outcome,
            String reasonCode) {
        return new AuditEvent(
                ids.newId(),
                clock.instant().truncatedTo(ChronoUnit.MICROS),
                context.requestId(),
                context.actorKeyId(),
                action,
                resourceType,
                resourceId,
                outcome,
                reasonCode,
                context.clientIpHash());
    }
}
