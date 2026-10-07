package io.github.harshmittal.urlshortener.shared.id.domain;

import java.util.UUID;

/** Source of unique identifiers, injected so callers stay deterministic under test. */
@FunctionalInterface
public interface IdGenerator {

    UUID newId();
}
