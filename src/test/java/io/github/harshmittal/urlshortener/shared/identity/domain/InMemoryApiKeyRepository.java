package io.github.harshmittal.urlshortener.shared.identity.domain;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class InMemoryApiKeyRepository implements ApiKeyRepository {

    private final Map<String, ApiKey> byPrefix = new LinkedHashMap<>();

    @Override
    public void insert(ApiKey key) {
        if (byPrefix.putIfAbsent(key.prefix(), key) != null) {
            throw new IllegalStateException("duplicate key prefix");
        }
    }

    @Override
    public Optional<ApiKey> findByPrefix(String prefix) {
        return Optional.ofNullable(byPrefix.get(prefix));
    }

    public List<ApiKey> all() {
        return List.copyOf(byPrefix.values());
    }
}
