package io.github.harshmittal.urlshortener.link.domain;

/** Stable reasons returned in {@code url-rejected} responses and audit events (spec error codes). */
public enum UrlRejectionReason {
    SCHEME_NOT_ALLOWED,
    HOST_NOT_ALLOWED,
    USERINFO_NOT_ALLOWED,
    SELF_REFERENCE,
    MALFORMED,
    TOO_LONG
}
