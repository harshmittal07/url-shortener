package io.github.harshmittal.urlshortener.support;

import java.util.Map;
import org.springframework.boot.flyway.autoconfigure.FlywayConfigurationCustomizer;
import org.springframework.boot.flyway.autoconfigure.FlywayConnectionDetails;
import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * The application connects as the application user; Flyway migrates as the migration user (R21,
 * A10). Activate the {@code integration} profile so Flyway runs, and add {@link
 * GeneratedRequiredVariables} for the required variables.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    JdbcConnectionDetails appUserConnectionDetails() {
        return new JdbcConnectionDetails() {
            @Override
            public String getUsername() {
                return PostgresTestDatabase.APP_USER;
            }

            @Override
            public String getPassword() {
                return PostgresTestDatabase.appPassword();
            }

            @Override
            public String getJdbcUrl() {
                return PostgresTestDatabase.jdbcUrl();
            }
        };
    }

    @Bean
    FlywayConnectionDetails migrationUserConnectionDetails() {
        return new FlywayConnectionDetails() {
            @Override
            public String getUsername() {
                return PostgresTestDatabase.MIGRATION_USER;
            }

            @Override
            public String getPassword() {
                return PostgresTestDatabase.migrationPassword();
            }

            @Override
            public String getJdbcUrl() {
                return PostgresTestDatabase.jdbcUrl();
            }
        };
    }

    @Bean
    FlywayConfigurationCustomizer appUserPlaceholder() {
        return configuration -> configuration.placeholders(Map.of("appUser", PostgresTestDatabase.APP_USER));
    }
}
