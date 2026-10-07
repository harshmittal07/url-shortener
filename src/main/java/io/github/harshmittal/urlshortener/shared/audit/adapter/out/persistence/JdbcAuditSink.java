package io.github.harshmittal.urlshortener.shared.audit.adapter.out.persistence;

import io.github.harshmittal.urlshortener.shared.audit.domain.AuditEvent;
import io.github.harshmittal.urlshortener.shared.audit.domain.AuditSink;
import java.sql.Timestamp;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Insert-only: the application user cannot update or delete audit rows (S-11). */
public final class JdbcAuditSink implements AuditSink {

    private final JdbcClient jdbc;

    public JdbcAuditSink(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void append(AuditEvent event) {
        jdbc.sql("""
                        INSERT INTO audit.audit_events (id, occurred_at, request_id, actor_key_id, action,
                            resource_type, resource_id, outcome, reason_code, client_ip_hash)
                        VALUES (:id, :occurredAt, :requestId, :actorKeyId, :action,
                            :resourceType, :resourceId, :outcome, :reasonCode, :clientIpHash)
                        """)
                .param("id", event.id())
                .param("occurredAt", Timestamp.from(event.occurredAt()))
                .param("requestId", event.requestId())
                .param("actorKeyId", event.actorKeyId())
                .param("action", event.action().name())
                .param("resourceType", event.resourceType())
                .param("resourceId", event.resourceId())
                .param("outcome", event.outcome().name())
                .param("reasonCode", event.reasonCode())
                .param("clientIpHash", event.clientIpHash())
                .update();
    }
}
