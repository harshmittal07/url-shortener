package io.github.harshmittal.urlshortener.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DailyQuotaSettingTest {

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {"1", "500", "999999999"})
    @DisplayName("AC18 (spec 02): a whole number of at least 1 is the quota")
    void acceptsWholeNumbers(String raw) {
        assertThat(DailyQuotaSetting.parse(raw)).isEqualTo(Integer.parseInt(raw));
    }

    @ParameterizedTest(name = "[{index}] \"{0}\"")
    @ValueSource(strings = {"0", "000", "-7", "+5", "abc", "2.5", "", " ", " 42", "1000000000", "99999999999"})
    @DisplayName("AC19 (spec 02): anything else fails, naming LINK_DAILY_QUOTA but not the value")
    void rejectsEverythingElse(String raw) {
        assertThatThrownBy(() -> DailyQuotaSetting.parse(raw))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Invalid configuration: LINK_DAILY_QUOTA must be a whole number of at least 1");
    }

    @Test
    @DisplayName("AC19 (spec 02): a missing value fails like an invalid one")
    void rejectsNull() {
        assertThatThrownBy(() -> DailyQuotaSetting.parse(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("LINK_DAILY_QUOTA");
    }
}
