package io.github.harshmittal.urlshortener.shared.audit.domain;

import java.time.Instant;
import java.util.UUID;

/** One row of {@code audit.audit_events} (S-10). Holds no key, full target URL or raw IP. */
public record AuditEvent(
        UUID id,
        Instant occurredAt,
        String requestId,
        UUID actorKeyId,
        AuditAction action,
        String resourceType,
        String resourceId,
        Outcome outcome,
        String reasonCode,
        String clientIpHash) {}
