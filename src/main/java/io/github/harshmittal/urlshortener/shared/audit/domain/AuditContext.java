package io.github.harshmittal.urlshortener.shared.audit.domain;

import java.util.UUID;

/**
 * Who did something and from where, carried from the web layer into use cases.
 *
 * @param actorKeyId the caller's key ID, or {@code null} when unauthenticated
 * @param clientIpHash keyed hash of the client IP (A6), never the raw IP
 */
public record AuditContext(String requestId, UUID actorKeyId, String clientIpHash) {}
