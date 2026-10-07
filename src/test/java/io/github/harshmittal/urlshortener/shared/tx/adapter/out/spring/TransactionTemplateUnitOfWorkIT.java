package io.github.harshmittal.urlshortener.shared.tx.adapter.out.spring;

import io.github.harshmittal.urlshortener.shared.tx.domain.UnitOfWork;
import io.github.harshmittal.urlshortener.shared.tx.domain.UnitOfWorkContract;
import io.github.harshmittal.urlshortener.support.IntegrationTest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;

/** Markers are audit rows: the one table the app user can only append to, so they are harmless. */
@IntegrationTest
class TransactionTemplateUnitOfWorkIT extends UnitOfWorkContract {

    @Autowired
    JdbcClient jdbc;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Override
    protected UnitOfWork unitOfWork() {
        return new TransactionTemplateUnitOfWork(transactionManager);
    }

    @Override
    protected void writeMarker(String marker) {
        jdbc.sql("""
                        INSERT INTO audit.audit_events (id, occurred_at, request_id, action, outcome)
                        VALUES (:id, :at, :marker, 'LINK_CREATED', 'SUCCESS')
                        """)
                .param("id", UUID.randomUUID())
                .param("at", Timestamp.from(Instant.parse("2026-10-07T00:00:00Z")))
                .param("marker", marker)
                .update();
    }

    @Override
    protected boolean markerExists(String marker) {
        return jdbc.sql("SELECT count(*) FROM audit.audit_events WHERE request_id = :marker")
                        .param("marker", marker)
                        .query(Integer.class)
                        .single()
                > 0;
    }
}
