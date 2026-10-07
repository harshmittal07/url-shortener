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
}
