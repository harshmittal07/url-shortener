package io.github.harshmittal.urlshortener.shared.audit.domain;

/** Audited actions (S-10, R17). Further actions arrive with the specs that use them. */
public enum AuditAction {
    API_KEY_CREATED,
    LINK_CREATED,
    LINK_DELETED,
    AUTH_FAILED,
    ACCESS_DENIED,
    RATE_LIMITED,
    URL_REJECTED
}
