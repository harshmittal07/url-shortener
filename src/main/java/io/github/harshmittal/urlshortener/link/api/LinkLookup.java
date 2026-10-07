package io.github.harshmittal.urlshortener.link.api;

import java.util.Optional;

/**
 * The link module's public read API (R14). Other modules resolve codes only through this
 * interface, never through link tables, so it can become a network call at extraction (D7).
 */
public interface LinkLookup {

    /** The active link for a code; empty for unknown, deleted or malformed codes. */
    Optional<ActiveLink> findActive(String code);
}
