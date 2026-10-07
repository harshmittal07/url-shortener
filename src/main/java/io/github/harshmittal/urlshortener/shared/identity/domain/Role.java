package io.github.harshmittal.urlshortener.shared.identity.domain;

public enum Role {
    /** Holds the bootstrap admin key; manages keys only (A3). */
    ADMIN,
    /** Holds an owner key; manages its own links. */
    OWNER
}
