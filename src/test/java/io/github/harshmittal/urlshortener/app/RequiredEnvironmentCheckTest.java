package io.github.harshmittal.urlshortener.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class RequiredEnvironmentCheckTest {

    private static final String SALT = "salt-value-0123456789abcdefghijklmnop";
    private static final String ADMIN_HASH = "ab".repeat(32);
    private static final String APP_USER = "app-user-value";
    private static final String APP_PASSWORD = "app-password-value";
    private static final String BASE_URL = "https://base-url-value.example";

    private static Map<String, String> valid() {
        Map<String, String> variables = new HashMap<>();
        variables.put("PUBLIC_BASE_URL", BASE_URL);
        variables.put("IP_HASH_SALT", SALT);
        variables.put("BOOTSTRAP_ADMIN_KEY_HASH", ADMIN_HASH);
        variables.put("DB_APP_USER", APP_USER);
        variables.put("DB_APP_PASSWORD", APP_PASSWORD);
        return variables;
    }

    @Test
    @DisplayName("AC36: a complete, well-formed environment passes and yields the settings")
    void validEnvironmentPasses() {
        RequiredEnvironmentCheck.Settings settings = RequiredEnvironmentCheck.verify(valid()::get);

        assertThat(settings.publicBaseUrl()).isEqualTo(BASE_URL);
        assertThat(settings.ipHashSalt()).isEqualTo(SALT);
        assertThat(settings.bootstrapAdminKeyHash()).isEqualTo(ADMIN_HASH);
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(
            strings = {"PUBLIC_BASE_URL", "IP_HASH_SALT", "BOOTSTRAP_ADMIN_KEY_HASH", "DB_APP_USER", "DB_APP_PASSWORD"})
    @DisplayName("AC36: a missing variable stops startup with a message naming it and no value")
    void missingVariableIsNamed(String name) {
        Map<String, String> variables = valid();
        variables.remove(name);

        assertThatThrownBy(() -> RequiredEnvironmentCheck.verify(variables::get))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(name)
                .satisfies(e -> assertNoValueIn(e.getMessage()));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(
            strings = {"PUBLIC_BASE_URL", "IP_HASH_SALT", "BOOTSTRAP_ADMIN_KEY_HASH", "DB_APP_USER", "DB_APP_PASSWORD"})
    @DisplayName("AC36: a blank variable counts as missing")
    void blankVariableIsMissing(String name) {
        Map<String, String> variables = valid();
        variables.put(name, "   ");

        assertThatThrownBy(() -> RequiredEnvironmentCheck.verify(variables::get))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(name);
    }

    static Stream<Arguments> malformed() {
        return Stream.of(
                Arguments.of("PUBLIC_BASE_URL", "ftp://malformed-value.example"),
                Arguments.of("PUBLIC_BASE_URL", "malformed-value"),
                Arguments.of("PUBLIC_BASE_URL", "https://"),
                Arguments.of("PUBLIC_BASE_URL", "https://malformed value.example"),
                Arguments.of("IP_HASH_SALT", "malformed-value-31-characters!!"),
                Arguments.of("BOOTSTRAP_ADMIN_KEY_HASH", "AB".repeat(32)),
                Arguments.of("BOOTSTRAP_ADMIN_KEY_HASH", "ab".repeat(31)),
                Arguments.of("BOOTSTRAP_ADMIN_KEY_HASH", "zz".repeat(32)));
    }

    @ParameterizedTest(name = "[{index}] {0}={1}")
    @MethodSource("malformed")
    @DisplayName("AC36: a malformed variable stops startup with a message naming it, never its value")
    void malformedVariableIsNamedWithoutValue(String name, String value) {
        Map<String, String> variables = valid();
        variables.put(name, value);

        assertThatThrownBy(() -> RequiredEnvironmentCheck.verify(variables::get))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(name)
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain(value))
                .satisfies(e -> assertNoValueIn(e.getMessage()));
    }

    @Test
    @DisplayName("AC36: every problem is reported at once")
    void reportsEveryProblem() {
        Map<String, String> variables = valid();
        variables.remove("DB_APP_PASSWORD");
        variables.put("IP_HASH_SALT", "short");

        assertThatThrownBy(() -> RequiredEnvironmentCheck.verify(variables::get))
                .hasMessageContaining("DB_APP_PASSWORD")
                .hasMessageContaining("IP_HASH_SALT")
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("short"));
    }

    private static void assertNoValueIn(String message) {
        assertThat(message)
                .doesNotContain(BASE_URL)
                .doesNotContain("base-url-value")
                .doesNotContain(SALT)
                .doesNotContain(ADMIN_HASH)
                .doesNotContain(APP_USER)
                .doesNotContain(APP_PASSWORD);
    }
}
