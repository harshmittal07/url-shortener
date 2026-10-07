package io.github.harshmittal.urlshortener.link.domain;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.harshmittal.urlshortener.link.domain.UrlPolicyDecision.Accepted;
import io.github.harshmittal.urlshortener.link.domain.UrlPolicyDecision.Rejected;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** T2 covers S-01 only. T4 adds S-02 to S-05 here. */
class StandardUrlPolicyTest {

    private final StandardUrlPolicy policy = new StandardUrlPolicy();

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(
            strings = {
                "javascript:alert(1)",
                "JaVaScRiPt:alert(1)",
                "data:text/html,<script>alert(1)</script>",
                "data:text/html;base64,PHNjcmlwdD4=",
                "file:///etc/passwd",
                "ftp://example.com/file",
                "mailto:someone@example.com",
                "//example.com/no-scheme",
                "example.com/no-scheme"
            })
    @DisplayName("AC12, S-01: a scheme other than http or https is rejected with SCHEME_NOT_ALLOWED")
    void rejectsOtherSchemes(String target) {
        assertThat(policy.evaluate(target)).isEqualTo(new Rejected(UrlRejectionReason.SCHEME_NOT_ALLOWED));
    }

    @ParameterizedTest(name = "[{index}] {0} -> {1}")
    @CsvSource({
        "https://example.com/a?b=c#d, https://example.com/a?b=c#d",
        "http://example.com, http://example.com",
        "HTTPS://example.com/X, https://example.com/X",
        "' https://example.com/trimmed ', https://example.com/trimmed"
    })
    @DisplayName("S-01: http and https are accepted, compared case-insensitively after trimming")
    void acceptsHttpAndHttps(String target, String normalized) {
        assertThat(policy.evaluate(target)).isEqualTo(new Accepted(normalized));
    }
}
