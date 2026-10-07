package io.github.harshmittal.urlshortener.support;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * One PostgreSQL 16 container per test JVM, with the migration and application users created by the
 * same init script Docker Compose uses (R21, A10).
 */
public final class PostgresTestDatabase {

    public static final String MIGRATION_USER = "urlshortener_migration";
    public static final String APP_USER = "urlshortener_app";

    // Generated per run so no credential literal lives in the repository.
    private static final String MIGRATION_PASSWORD = UUID.randomUUID().toString();
    private static final String APP_PASSWORD = UUID.randomUUID().toString();

    private static final PostgreSQLContainer CONTAINER = start();

    private PostgresTestDatabase() {}

    private static PostgreSQLContainer start() {
        PostgreSQLContainer container = new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))
                .withEnv("DB_MIGRATION_USER", MIGRATION_USER)
                .withEnv("DB_MIGRATION_PASSWORD", MIGRATION_PASSWORD)
                .withEnv("DB_APP_USER", APP_USER)
                .withEnv("DB_APP_PASSWORD", APP_PASSWORD)
                .withCopyFileToContainer(
                        MountableFile.forHostPath("docker/postgres/initdb/01-users.sh", 0755),
                        "/docker-entrypoint-initdb.d/01-users.sh");
        container.start();
        return container;
    }

    public static String jdbcUrl() {
        return CONTAINER.getJdbcUrl();
    }

    public static String migrationPassword() {
        return MIGRATION_PASSWORD;
    }

    public static String appPassword() {
        return APP_PASSWORD;
    }

    /** A connection as the application user, outside the Spring context. */
    public static Connection connectAsAppUser() throws SQLException {
        return DriverManager.getConnection(jdbcUrl(), APP_USER, APP_PASSWORD);
    }
}
