package io.github.harshmittal.urlshortener.shared.identity.adapter.in.web;

import io.github.harshmittal.urlshortener.shared.identity.domain.AuthenticationResult;
import io.github.harshmittal.urlshortener.shared.identity.domain.AuthenticationResult.Authenticated;
import io.github.harshmittal.urlshortener.shared.identity.domain.AuthenticationResult.Failed;
import io.github.harshmittal.urlshortener.shared.identity.domain.AuthenticationResult.Reason;
import io.github.harshmittal.urlshortener.shared.identity.domain.Authenticator;
import io.github.harshmittal.urlshortener.shared.identity.domain.Principal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates {@code Authorization: Bearer <key>} on {@code /api/**}. On failure it records the
 * reason and leaves the request anonymous; authorization then rejects it through the entry point,
 * which audits and answers {@code 401}. The key is never logged.
 */
public final class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final String FAILURE_ATTRIBUTE = ApiKeyAuthenticationFilter.class.getName() + ".failure";
    private static final String BEARER = "Bearer ";

    private final Authenticator authenticator;

    public ApiKeyAuthenticationFilter(Authenticator authenticator) {
        this.authenticator = authenticator;
    }

    static Reason failureOf(HttpServletRequest request) {
        return request.getAttribute(FAILURE_ATTRIBUTE) instanceof Reason reason ? reason : Reason.MISSING;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !(path.equals("/api") || path.startsWith("/api/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        AuthenticationResult result = authenticator.authenticate(bearerToken(request));
        switch (result) {
            case Authenticated authenticated -> authenticate(authenticated.principal());
            case Failed failed -> request.setAttribute(FAILURE_ATTRIBUTE, failed.reason());
        }
        chain.doFilter(request, response);
    }

    /** The token after {@code Bearer }, the raw header for any other scheme, or null when absent. */
    private static String bearerToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.regionMatches(true, 0, BEARER, 0, BEARER.length())) {
            return header.substring(BEARER.length()).trim();
        }
        return header;
    }

    private static void authenticate(Principal principal) {
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal.keyId().toString(),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + principal.role().name())));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
    }
}
