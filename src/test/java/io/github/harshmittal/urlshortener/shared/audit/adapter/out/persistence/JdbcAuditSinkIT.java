package io.github.harshmittal.urlshortener.shared.audit.adapter.out.persistence;

import io.github.harshmittal.urlshortener.shared.audit.domain.AuditAction;
import io.github.harshmittal.urlshortener.shared.audit.domain.AuditEvent;
import io.github.harshmittal.urlshortener.shared.audit.domain.AuditSink;
import io.github.harshmittal.urlshortener.shared.audit.domain.AuditSinkContract;
import io.github.harshmittal.urlshortener.shared.audit.domain.Outcome;
import io.github.harshmittal.urlshortener.support.IntegrationTest;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

@IntegrationTest
class JdbcAuditSinkIT extends AuditSinkContract {

    @Autowired
    JdbcClient jdbc;

    @Override
    protected AuditSink sink() {
        return new JdbcAuditSink(jdbc);
    }

    @Override
    protected Optional<AuditEvent> stored(UUID id) {
        return jdbc.sql("SELECT * FROM audit.audit_events WHERE id = :id")
                .param("id", id)
                .query((row, rowNumber) -> new AuditEvent(
                        row.getObject("id", UUID.class),
                        row.getTimestamp("occurred_at").toInstant(),
                        row.getString("request_id"),
                        row.getObject("actor_key_id", UUID.class),
                        AuditAction.valueOf(row.getString("action")),
                        row.getString("resource_type"),
                        row.getString("resource_id"),
                        Outcome.valueOf(row.getString("outcome")),
                        row.getString("reason_code"),
                        row.getString("client_ip_hash")))
                .optional();
    }
}
