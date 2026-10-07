package io.github.harshmittal.urlshortener.link.domain;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class InMemoryLinkRepository implements LinkRepository {

    private final Map<ShortCode, Link> byCode = new LinkedHashMap<>();

    @Override
    public boolean insertIfCodeFree(Link link) {
        return byCode.putIfAbsent(link.code(), link) == null;
    }

    @Override
    public Optional<Link> findByCode(ShortCode code) {
        return Optional.ofNullable(byCode.get(code));
    }

    @Override
    public boolean markDeleted(ShortCode code) {
        Link link = byCode.get(code);
        if (link == null || !link.isActive()) {
            return false;
        }
        byCode.put(
                code,
                new Link(
                        link.id(),
                        link.code(),
                        link.targetUrl(),
                        link.ownerKeyId(),
                        LinkStatus.DELETED,
                        link.createdAt()));
        return true;
    }

    public List<Link> all() {
        return List.copyOf(byCode.values());
    }
}
