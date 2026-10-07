package io.github.harshmittal.urlshortener.shared.identity.domain;

import java.util.Optional;

public interface ApiKeyRepository {

    void insert(ApiKey key);

    Optional<ApiKey> findByPrefix(String prefix);
}
