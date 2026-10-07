package io.github.harshmittal.urlshortener.support;

import java.util.Map;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;

/**
 * The required variables (R22) that are generated per run, so they have no literal in the
 * repository. Added before the context refreshes: with a real server, filters are created while the
 * server starts, earlier than a {@code DynamicPropertyRegistrar} applies.
 */
public class GeneratedRequiredVariables implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    public static Map<String, Object> values() {
        return Map.of(
                "BOOTSTRAP_ADMIN_KEY_HASH", TestAdminKey.hash(),
                "DB_APP_USER", PostgresTestDatabase.APP_USER,
                "DB_APP_PASSWORD", PostgresTestDatabase.appPassword());
    }

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        context.getEnvironment()
                .getPropertySources()
                .addFirst(new MapPropertySource("generatedRequiredVariables", values()));
    }
}
