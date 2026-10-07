package io.github.harshmittal.urlshortener.app;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.harshmittal.urlshortener.support.IntegrationTest;
import io.github.harshmittal.urlshortener.support.PostgresTestDatabase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

@IntegrationTest
class UrlShortenerApplicationTests {

    @Autowired
    JdbcClient jdbc;

    @Test
    @DisplayName("R21: the application serves requests as the application user, not the migration user")
    void connectsAsApplicationUser() {
        String user = jdbc.sql("SELECT current_user").query(String.class).single();

        assertThat(user).isEqualTo(PostgresTestDatabase.APP_USER);
    }
}
