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

    public List<Link> all() {
        return List.copyOf(byCode.values());
    }
}
