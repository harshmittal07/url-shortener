package io.github.harshmittal.urlshortener.redirect.domain;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.harshmittal.urlshortener.link.api.ActiveLink;
import io.github.harshmittal.urlshortener.link.api.LinkLookup;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RedirectServiceTest {

    private final List<String> lookups = new ArrayList<>();
    private final LinkLookup links = code -> {
        lookups.add(code);
        return code.equals("aB3dE5g") ? Optional.of(new ActiveLink(code, "https://example.com/t")) : Optional.empty();
    };
    private final RedirectService service = new RedirectService(links);

    @Test
    @DisplayName("AC23: an active code resolves to its stored target")
    void resolvesActiveCode() {
        assertThat(service.resolve("aB3dE5g")).contains("https://example.com/t");
    }

    @Test
    @DisplayName("AC24: an unknown or deleted code resolves to nothing")
    void unknownCodeResolvesToNothing() {
        assertThat(service.resolve("zzzzzzz")).isEmpty();
    }

    @ParameterizedTest(name = "[{index}] \"{0}\"")
    @ValueSource(strings = {"", "abc", "abcdefgh", "abc-efg", "abc/efg", "abcdef/"})
    @DisplayName("AC24: a malformed code resolves to nothing without a lookup")
    void malformedCodeIsNotLookedUp(String code) {
        assertThat(service.resolve(code)).isEmpty();
        assertThat(lookups).isEmpty();
    }
}
