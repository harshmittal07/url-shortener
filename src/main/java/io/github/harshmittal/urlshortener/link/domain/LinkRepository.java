package io.github.harshmittal.urlshortener.link.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LinkRepository {

    /**
     * Inserts the link unless its code is already taken by any link, active or deleted (R7, R10).
     * Uniqueness is decided by the database, never by check-then-insert.
     *
     * @return false if the code was taken; the caller retries with a new code
     */
    boolean insertIfCodeFree(Link link);

    Optional<Link> findByCode(ShortCode code);

    /**
     * Soft-deletes an active link: its status becomes {@code DELETED} and the row keeps its code, so
     * the code is never issued again (R10, AC22).
     *
     * @return false if no active link has the code
     */
    boolean markDeleted(ShortCode code);

    /**
     * Counts the owner's links created in {@code [from, until)}, whatever their status: deleted links
     * count (spec 02 R1, R2).
     */
    long countCreatedBy(UUID ownerKeyId, Instant from, Instant until);

    /**
     * The owner's {@code ACTIVE} links, newest first, ties by code in descending binary order, at
     * most {@code limit} of them. Only the owner's links, by the query itself (spec 03 R2, R3, S-08).
     */
    List<Link> findActiveByOwner(UUID ownerKeyId, int limit);
}
