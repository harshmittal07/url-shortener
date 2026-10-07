package io.github.harshmittal.urlshortener.shared.identity.domain;

class InMemoryApiKeyRepositoryTest extends ApiKeyRepositoryContract {

    private final InMemoryApiKeyRepository repository = new InMemoryApiKeyRepository();

    @Override
    protected ApiKeyRepository repository() {
        return repository;
    }
}
