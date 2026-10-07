package io.github.harshmittal.urlshortener.shared.audit.domain;

/** Append-only store for audit events (S-10, S-11). */
public interface AuditSink {

    void append(AuditEvent event);
}
