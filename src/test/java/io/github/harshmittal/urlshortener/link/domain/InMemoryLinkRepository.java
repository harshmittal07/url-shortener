package io.github.harshmittal.urlshortener.link.domain;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

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

    @Override
    public long countCreatedBy(UUID ownerKeyId, Instant from, Instant until) {
        return byCode.values().stream()
                .filter(link -> link.ownerKeyId().equals(ownerKeyId))
                .filter(link ->
                        !link.createdAt().isBefore(from) && link.createdAt().isBefore(until))
                .count();
    }

    @Override
    public List<Link> findActiveByOwner(UUID ownerKeyId, int limit) {
        return byCode.values().stream()
                .filter(link -> link.ownerKeyId().equals(ownerKeyId))
                .filter(Link::isActive)
                .sorted(Comparator.comparing(Link::createdAt)
                        .thenComparing(link -> link.code().value())
                        .reversed())
                .limit(limit)
                .toList();
    }

    public List<Link> all() {
        return List.copyOf(byCode.values());
    }
}
