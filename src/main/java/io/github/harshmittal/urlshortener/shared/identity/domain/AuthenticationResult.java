package io.github.harshmittal.urlshortener.shared.identity.domain;

import java.util.Optional;

/** Either a principal or the reason authentication failed, recorded as the audit reason code. */
public sealed interface AuthenticationResult {

    record Authenticated(Principal principal) implements AuthenticationResult {}

    record Failed(Reason reason) implements AuthenticationResult {}

    enum Reason {
        MISSING,
        MALFORMED,
        UNKNOWN,
        REVOKED
    }

    default Optional<Principal> authenticatedPrincipal() {
        return this instanceof Authenticated authenticated ? Optional.of(authenticated.principal()) : Optional.empty();
    }
}
