package io.github.harshmittal.urlshortener.link.domain;

import io.github.harshmittal.urlshortener.link.api.LinkLookup;
import java.time.Instant;
import java.util.UUID;

class LinkLookupServiceTest extends LinkLookupContract {

    private final InMemoryLinkRepository links = new InMemoryLinkRepository();

    @Override
    protected LinkLookup lookup() {
        return new LinkLookupService(links);
    }

    @Override
    protected void givenLink(ShortCode code, String targetUrl, LinkStatus status) {
        links.insertIfCodeFree(new Link(UUID.randomUUID(), code, targetUrl, UUID.randomUUID(), status, Instant.EPOCH));
    }
}
