package io.github.harshmittal.urlshortener.link.domain;

class InMemoryLinkRepositoryTest extends LinkRepositoryContract {

    private final InMemoryLinkRepository repository = new InMemoryLinkRepository();

    @Override
    protected LinkRepository repository() {
        return repository;
    }
}
