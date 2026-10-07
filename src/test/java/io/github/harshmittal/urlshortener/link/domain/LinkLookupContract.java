package io.github.harshmittal.urlshortener.link.domain;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.harshmittal.urlshortener.link.api.ActiveLink;
import io.github.harshmittal.urlshortener.link.api.LinkLookup;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Contract for every implementation of the public {@link LinkLookup} API (link.api). */
public abstract class LinkLookupContract {

    protected abstract LinkLookup lookup();

    /** Arranges a stored link with the given code, target and status. */
    protected abstract void givenLink(ShortCode code, String targetUrl, LinkStatus status);

    @Test
    @DisplayName("R12: an active link is found with its target")
    void findsActiveLink() {
        ShortCode code = TestCodes.random();
        givenLink(code, "https://example.com/a", LinkStatus.ACTIVE);

        assertThat(lookup().findActive(code.value())).contains(new ActiveLink(code.value(), "https://example.com/a"));
    }

    @Test
    @DisplayName("R13: a deleted link is not found")
    void deletedLinkIsNotFound() {
        ShortCode code = TestCodes.random();
        givenLink(code, "https://example.com/d", LinkStatus.DELETED);

        assertThat(lookup().findActive(code.value())).isEmpty();
    }

    @Test
    @DisplayName("R13: an unknown code is not found")
    void unknownCodeIsNotFound() {
        assertThat(lookup().findActive(TestCodes.random().value())).isEmpty();
    }

    @ParameterizedTest(name = "[{index}] \"{0}\"")
    @ValueSource(strings = {"", "abc", "abcdefgh", "abc-efg", "abc efg", "äbcdefg"})
    @DisplayName("R13: a malformed code is not found")
    void malformedCodeIsNotFound(String code) {
        assertThat(lookup().findActive(code)).isEmpty();
    }
}
