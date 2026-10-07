package io.github.harshmittal.urlshortener.link.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Contract for every production {@link ShortCodeGenerator} adapter. */
public abstract class ShortCodeGeneratorContract {

    protected abstract ShortCodeGenerator generator();

    @Test
    @DisplayName("AC8, S-06: codes are 7 Base62 characters")
    void producesSevenBase62Characters() {
        for (int i = 0; i < 1_000; i++) {
            assertThat(generator().next().value()).matches("[0-9A-Za-z]{7}");
        }
    }

    @Test
    @DisplayName("S-06: codes do not repeat across a sample")
    void doesNotRepeat() {
        Set<ShortCode> codes = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            codes.add(generator().next());
        }

        assertThat(codes).hasSize(10_000);
    }
}
