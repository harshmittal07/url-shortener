package io.github.harshmittal.urlshortener.shared.web;

import io.github.harshmittal.urlshortener.shared.audit.domain.AuditContext;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Builds the audit context for the current request: request ID, the caller's key ID (the
 * authentication name) and the hashed socket address. {@code X-Forwarded-For} is ignored (A6).
 */
public final class RequestAuditContexts {

    private final ClientIpHasher ipHasher;

    public RequestAuditContexts(ClientIpHasher ipHasher) {
        this.ipHasher = ipHasher;
    }

    public AuditContext of(HttpServletRequest request) {
        return new AuditContext(
                RequestIdFilter.requestIdOf(request),
                callerKeyId(SecurityContextHolder.getContext().getAuthentication()),
                ipHasher.hash(request.getRemoteAddr()));
    }

    /** The authenticated caller's key ID, or {@code null} for anonymous requests. */
    public static UUID callerKeyId(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }
        try {
            return UUID.fromString(authentication.getName());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
