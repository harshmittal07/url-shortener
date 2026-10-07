package io.github.harshmittal.urlshortener.migration;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.harshmittal.urlshortener.support.IntegrationTest;
import io.github.harshmittal.urlshortener.support.PostgresTestDatabase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

@IntegrationTest
class SchemaIT {

    @Autowired
    JdbcClient jdbc;

    @Test
    @DisplayName("AC7: api_keys lives only in the identity schema")
    void apiKeysLiveInIdentitySchema() {
        var schemas = jdbc.sql("SELECT table_schema FROM information_schema.tables WHERE table_name = 'api_keys'")
                .query(String.class)
                .list();

        assertThat(schemas).containsExactly("identity");
    }

    @Test
    @DisplayName("R21: the migration user owns every schema Flyway created")
    void migrationUserOwnsSchemas() {
        var owners = jdbc.sql("""
                        SELECT DISTINCT pg_get_userbyid(nspowner) FROM pg_namespace
                        WHERE nspname IN ('flyway', 'identity', 'link', 'audit')
                        """).query(String.class).list();

        assertThat(owners).containsExactly(PostgresTestDatabase.MIGRATION_USER);
    }

    @Test
    @DisplayName("AC7: each table lives in the schema of the module that owns it")
    void tablesLiveInOwningSchemas() {
        var tables = jdbc.sql("""
                        SELECT table_schema || '.' || table_name
                        FROM information_schema.tables
                        WHERE table_schema IN ('identity', 'link', 'audit')
                        """).query(String.class).list();

        assertThat(tables).containsExactlyInAnyOrder("identity.api_keys", "link.links", "audit.audit_events");
    }

    @Test
    @DisplayName("AC7, S-20: links.owner_key_id has no foreign key, and no foreign key crosses schemas")
    void noForeignKeyCrossesSchemas() {
        int linkForeignKeys = jdbc.sql(
                        "SELECT count(*) FROM pg_constraint WHERE contype = 'f' AND conrelid = 'link.links'::regclass")
                .query(Integer.class)
                .single();
        int crossSchemaForeignKeys = jdbc.sql("""
                        SELECT count(*)
                        FROM pg_constraint c
                        JOIN pg_class src ON src.oid = c.conrelid
                        JOIN pg_class dst ON dst.oid = c.confrelid
                        WHERE c.contype = 'f' AND src.relnamespace <> dst.relnamespace
                        """).query(Integer.class).single();

        assertThat(linkForeignKeys).isZero();
        assertThat(crossSchemaForeignKeys).isZero();
    }

    @Test
    @DisplayName("R7: short-code uniqueness is enforced by a database constraint")
    void codeIsUniqueByConstraint() {
        int uniqueOnCode = jdbc.sql("""
                        SELECT count(*) FROM pg_constraint
                        WHERE conrelid = 'link.links'::regclass AND contype = 'u' AND conname = 'links_code_uk'
                        """).query(Integer.class).single();

        assertThat(uniqueOnCode).isEqualTo(1);
    }

    @Test
    @DisplayName("AC20 (spec 02): an index on link.links (owner_key_id, created_at) serves the daily quota count")
    void dailyQuotaCountIsIndexed() {
        String definition = jdbc.sql("""
                        SELECT indexdef FROM pg_indexes
                        WHERE schemaname = 'link' AND tablename = 'links' AND indexname = 'links_owner_created_idx'
                        """).query(String.class).optional().orElse(null);

        assertThat(definition).isNotNull().endsWith("(owner_key_id, created_at)");
    }
}
