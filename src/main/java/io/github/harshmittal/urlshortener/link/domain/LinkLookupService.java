package io.github.harshmittal.urlshortener.link.domain;

import io.github.harshmittal.urlshortener.link.api.ActiveLink;
import io.github.harshmittal.urlshortener.link.api.LinkLookup;
import java.util.Optional;

/** Implements the link module's public read API. Read-only: performs no writes (R14). */
public final class LinkLookupService implements LinkLookup {

    private final LinkRepository links;

    public LinkLookupService(LinkRepository links) {
        this.links = links;
    }

    @Override
    public Optional<ActiveLink> findActive(String code) {
        return ShortCode.parse(code)
                .flatMap(links::findByCode)
                .filter(Link::isActive)
                .map(link -> new ActiveLink(link.code().value(), link.targetUrl()));
    }
}
