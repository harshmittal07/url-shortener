package io.github.harshmittal.urlshortener.shared.id.adapter.out.random;

import io.github.harshmittal.urlshortener.shared.id.domain.IdGenerator;
import java.util.UUID;

/** Random (version 4) UUIDs, backed by {@code SecureRandom}. */
public final class RandomUuidGenerator implements IdGenerator {

    @Override
    public UUID newId() {
        return UUID.randomUUID();
    }
}
