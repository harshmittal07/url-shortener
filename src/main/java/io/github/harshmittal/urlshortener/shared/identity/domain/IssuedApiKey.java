package io.github.harshmittal.urlshortener.shared.identity.domain;

import java.time.Instant;
import java.util.UUID;

/** A newly issued key. The only place the plaintext key exists (R2); {@link #toString()} hides it. */
public record IssuedApiKey(UUID id, String key, Instant createdAt) {

    @Override
    public String toString() {
        return "IssuedApiKey[id=" + id + ", key=<redacted>, createdAt=" + createdAt + "]";
    }
}
