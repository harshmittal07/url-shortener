package io.github.harshmittal.urlshortener.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.harshmittal.urlshortener.support.IntegrationTest;
import io.github.harshmittal.urlshortener.support.PostgresTestDatabase;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Checks the application user's grants over a separate connection (A10). The Spring context is
 * loaded so Flyway has migrated the schema.
 */
@IntegrationTest
class DatabasePrivilegesIT {

    private static final String INSUFFICIENT_PRIVILEGE = "42501";

    @ParameterizedTest(name = "{0}")
    @ValueSource(
            strings = {
                "UPDATE audit.audit_events SET outcome = 'TAMPERED'",
                "DELETE FROM audit.audit_events",
                "TRUNCATE audit.audit_events"
            })
    @DisplayName("AC31, S-11: the application user cannot update, delete or truncate audit events")
    void auditEventsAreAppendOnly(String statement) {
        assertRejected(statement);
    }

    @Test
    @DisplayName("AC31, S-11: the application user can append and read audit events")
    void applicationUserCanInsertAndSelectAuditEvents() throws SQLException {
        UUID id = UUID.randomUUID();
        try (Connection connection = PostgresTestDatabase.connectAsAppUser()) {
            connection.setAutoCommit(false);
            try (PreparedStatement insert = connection.prepareStatement("""
                    INSERT INTO audit.audit_events (id, occurred_at, request_id, action, outcome)
                    VALUES (?, ?, 'privileges-it', 'LINK_CREATED', 'SUCCESS')
                    """)) {
                insert.setObject(1, id);
                insert.setTimestamp(2, Timestamp.from(Instant.parse("2026-10-07T00:00:00Z")));
                insert.executeUpdate();
            }
            try (PreparedStatement select =
                    connection.prepareStatement("SELECT count(*) FROM audit.audit_events WHERE id = ?")) {
                select.setObject(1, id);
                try (ResultSet rows = select.executeQuery()) {
                    rows.next();
                    assertThat(rows.getInt(1)).isEqualTo(1);
                }
            }
            connection.rollback();
        }
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(
            strings = {
                "CREATE TABLE identity.intruder (id int)",
                "CREATE TABLE link.intruder (id int)",
                "CREATE TABLE audit.intruder (id int)",
                "ALTER TABLE identity.api_keys ADD COLUMN intruder int",
                "ALTER TABLE link.links ADD COLUMN intruder int",
                "ALTER TABLE audit.audit_events ADD COLUMN intruder int",
                "DROP TABLE identity.api_keys",
                "DROP TABLE link.links",
                "DROP TABLE audit.audit_events",
                "CREATE SCHEMA intruder"
            })
    @DisplayName("AC34: the application user has no DDL rights")
    void applicationUserHasNoDdlRights(String statement) {
        assertRejected(statement);
    }

    @Test
    @DisplayName("R21: the application user may update only a link's status, not its target")
    void applicationUserCannotRewriteLinkTargets() {
        assertRejected("UPDATE link.links SET target_url = 'https://example.org'");
    }

    private static void assertRejected(String sql) {
        assertThatThrownBy(() -> {
                    try (Connection connection = PostgresTestDatabase.connectAsAppUser();
                            Statement statement = connection.createStatement()) {
                        statement.execute(sql);
                    }
                })
                .isInstanceOf(SQLException.class)
                .extracting(e -> ((SQLException) e).getSQLState())
                .isEqualTo(INSUFFICIENT_PRIVILEGE);
    }
}
