package io.github.harshmittal.urlshortener.link.adapter.out.persistence;

import io.github.harshmittal.urlshortener.link.domain.LinkRepository;
import io.github.harshmittal.urlshortener.link.domain.LinkRepositoryContract;
import io.github.harshmittal.urlshortener.support.IntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

@IntegrationTest
class JdbcLinkRepositoryIT extends LinkRepositoryContract {

    @Autowired
    JdbcClient jdbc;

    @Override
    protected LinkRepository repository() {
        return new JdbcLinkRepository(jdbc);
    }
}
