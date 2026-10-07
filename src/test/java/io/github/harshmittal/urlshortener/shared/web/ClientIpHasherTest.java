package io.github.harshmittal.urlshortener.shared.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ClientIpHasherTest {

    private final ClientIpHasher hasher = new ClientIpHasher("salt-for-unit-tests-0123456789abcdef");

    @Test
    @DisplayName("S-12, A6: the IP hash is 64 lowercase hex characters and never contains the IP")
    void hashesToHex() {
        assertThat(hasher.hash("203.0.113.7")).matches("[0-9a-f]{64}").doesNotContain("203.0.113.7");
    }

    @Test
    @DisplayName("A6: the same IP always hashes the same, so events can be correlated")
    void isDeterministic() {
        assertThat(hasher.hash("203.0.113.7")).isEqualTo(hasher.hash("203.0.113.7"));
        assertThat(hasher.hash("203.0.113.7")).isNotEqualTo(hasher.hash("203.0.113.8"));
    }

    @Test
    @DisplayName("A6: the hash depends on the salt, so it cannot be reversed with a public table")
    void dependsOnSalt() {
        assertThat(new ClientIpHasher("another-salt-0123456789abcdefghij").hash("203.0.113.7"))
                .isNotEqualTo(hasher.hash("203.0.113.7"));
    }

    @Test
    @DisplayName("R22: an empty salt is rejected")
    void rejectsEmptySalt() {
        assertThatThrownBy(() -> new ClientIpHasher("")).isInstanceOf(IllegalArgumentException.class);
    }
}
