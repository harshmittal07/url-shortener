package io.github.harshmittal.urlshortener.link.domain;

import java.time.Instant;
import java.util.UUID;

/** A short link. {@code ownerKeyId} is a key ID from identity, held without a foreign key (R5). */
public record Link(UUID id, ShortCode code, String targetUrl, UUID ownerKeyId, LinkStatus status, Instant createdAt) {

    public boolean isActive() {
        return status == LinkStatus.ACTIVE;
    }
}
