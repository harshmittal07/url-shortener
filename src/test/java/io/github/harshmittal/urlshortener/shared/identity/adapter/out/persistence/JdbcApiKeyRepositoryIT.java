package io.github.harshmittal.urlshortener.shared.identity.adapter.out.persistence;

import io.github.harshmittal.urlshortener.shared.identity.domain.ApiKeyRepository;
import io.github.harshmittal.urlshortener.shared.identity.domain.ApiKeyRepositoryContract;
import io.github.harshmittal.urlshortener.support.IntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

@IntegrationTest
class JdbcApiKeyRepositoryIT extends ApiKeyRepositoryContract {

    @Autowired
    JdbcClient jdbc;

    @Override
    protected ApiKeyRepository repository() {
        return new JdbcApiKeyRepository(jdbc);
    }
}
