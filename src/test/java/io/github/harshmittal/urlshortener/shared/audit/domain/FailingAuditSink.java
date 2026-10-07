package io.github.harshmittal.urlshortener.shared.audit.domain;

/** Simulates an unavailable audit store. */
public final class FailingAuditSink implements AuditSink {

    @Override
    public void append(AuditEvent event) {
        throw new IllegalStateException("audit store unavailable");
    }
}
