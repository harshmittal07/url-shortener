package io.github.harshmittal.urlshortener.shared.identity.domain;

import java.time.Instant;
import java.util.UUID;

/** A stored owner key: only its public prefix and SHA-256 hash, never the key itself (S-07). */
public record ApiKey(UUID id, String prefix, String keyHash, Instant createdAt, Instant revokedAt) {

    public boolean isRevoked() {
        return revokedAt != null;
    }
}
