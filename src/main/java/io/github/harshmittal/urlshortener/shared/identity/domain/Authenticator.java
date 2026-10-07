package io.github.harshmittal.urlshortener.shared.identity.domain;

/** Authenticates a bearer token (D4). OAuth or another scheme would be another implementation. */
public interface Authenticator {

    /** @param bearerToken the presented token, or {@code null} when none was sent */
    AuthenticationResult authenticate(String bearerToken);
}
