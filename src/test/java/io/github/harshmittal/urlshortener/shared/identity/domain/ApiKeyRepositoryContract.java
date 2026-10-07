package io.github.harshmittal.urlshortener.shared.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Contract for every {@link ApiKeyRepository} adapter. */
public abstract class ApiKeyRepositoryContract {

    private static final String BASE62 = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final SecureRandom RANDOM = new SecureRandom();

    protected abstract ApiKeyRepository repository();

    @Test
    @DisplayName("S-07: a stored key is found by its prefix with hash and timestamps intact")
    void findsByPrefix() {
        ApiKey key = newKey(null);

        repository().insert(key);

        assertThat(repository().findByPrefix(key.prefix())).contains(key);
    }

    @Test
    @DisplayName("S-07: a revoked key keeps its revocation time")
    void keepsRevocation() {
        ApiKey key = newKey(Instant.parse("2026-10-07T11:00:00Z"));

        repository().insert(key);

        assertThat(repository().findByPrefix(key.prefix()))
                .hasValueSatisfying(found -> assertThat(found.isRevoked()).isTrue());
    }

    @Test
    @DisplayName("S-07: an unknown prefix finds nothing")
    void unknownPrefixFindsNothing() {
        assertThat(repository().findByPrefix(randomPrefix())).isEmpty();
    }

    @Test
    @DisplayName("S-07: a second key with the same prefix is rejected")
    void rejectsDuplicatePrefix() {
        ApiKey first = newKey(null);
        repository().insert(first);
        ApiKey second = new ApiKey(UUID.randomUUID(), first.prefix(), "f".repeat(64), first.createdAt(), null);

        assertThatThrownBy(() -> repository().insert(second)).isInstanceOf(RuntimeException.class);
    }

    private static ApiKey newKey(Instant revokedAt) {
        return new ApiKey(
                UUID.randomUUID(), randomPrefix(), "e".repeat(64), Instant.parse("2026-10-07T10:00:00.5Z"), revokedAt);
    }

    private static String randomPrefix() {
        StringBuilder prefix = new StringBuilder();
        for (int i = 0; i < 12; i++) {
            prefix.append(BASE62.charAt(RANDOM.nextInt(BASE62.length())));
        }
        return prefix.toString();
    }
}
