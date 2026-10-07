package io.github.harshmittal.urlshortener.link.domain;

import java.util.Optional;

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
}
