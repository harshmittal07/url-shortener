package io.github.harshmittal.urlshortener.support;

import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Reads audit rows written for one request. */
public final class AuditRows {

    private AuditRows() {}

    public static List<Map<String, Object>> forRequest(JdbcClient jdbc, String requestId) {
        return jdbc.sql("SELECT * FROM audit.audit_events WHERE request_id = :requestId ORDER BY occurred_at")
                .param("requestId", requestId)
                .query()
                .listOfRows();
    }
}
