package io.github.harshmittal.urlshortener.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

class MigrationsContainNoSecretsTest {

    private static final Pattern INSERT = Pattern.compile("\\binsert\\s+into\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern API_KEY = Pattern.compile("usk_[A-Za-z0-9]");
    private static final Pattern SHA256_HEX = Pattern.compile("\\b[0-9a-fA-F]{64}\\b");

    private static List<Resource> migrations;

    @BeforeAll
    static void loadMigrations() throws IOException {
        migrations = List.of(new PathMatchingResourcePatternResolver().getResources("classpath:db/migration/*.sql"));
    }

    @Test
    @DisplayName("AC6: the migrations exist and are scanned")
    void migrationsAreFound() {
        assertThat(migrations).hasSizeGreaterThanOrEqualTo(3);
    }

    @Test
    @DisplayName("AC6: no migration inserts data, so none can seed an API key or other secret")
    void noMigrationInsertsData() throws IOException {
        for (Resource migration : migrations) {
            assertThat(INSERT.matcher(contentOf(migration)).find())
                    .as(migration.getFilename())
                    .isFalse();
        }
    }

    @Test
    @DisplayName("AC6: no migration contains an API key or a key hash literal")
    void noMigrationContainsKeyMaterial() throws IOException {
        for (Resource migration : migrations) {
            String sql = contentOf(migration);
            assertThat(API_KEY.matcher(sql).find()).as(migration.getFilename()).isFalse();
            assertThat(SHA256_HEX.matcher(sql).find())
                    .as(migration.getFilename())
                    .isFalse();
        }
    }

    private static String contentOf(Resource resource) throws IOException {
        return resource.getContentAsString(StandardCharsets.UTF_8);
    }
}
