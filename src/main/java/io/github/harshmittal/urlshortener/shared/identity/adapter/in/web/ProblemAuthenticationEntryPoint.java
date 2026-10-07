package io.github.harshmittal.urlshortener.shared.identity.adapter.in.web;

import io.github.harshmittal.urlshortener.shared.audit.domain.AuditAction;
import io.github.harshmittal.urlshortener.shared.audit.domain.AuditTrail;
import io.github.harshmittal.urlshortener.shared.identity.domain.AuthenticationResult.Reason;
import io.github.harshmittal.urlshortener.shared.web.RequestAuditContexts;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Audits {@code AUTH_FAILED} and answers {@code 401 unauthorized}. The body is rendered by Spring
 * MVC's exception resolver, so it matches every other problem response.
 */
public final class ProblemAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final Logger log = LoggerFactory.getLogger(ProblemAuthenticationEntryPoint.class);

    private final AuditTrail audit;
    private final RequestAuditContexts auditContexts;
    private final HandlerExceptionResolver exceptionResolver;

    public ProblemAuthenticationEntryPoint(
            AuditTrail audit, RequestAuditContexts auditContexts, HandlerExceptionResolver exceptionResolver) {
        this.audit = audit;
        this.auditContexts = auditContexts;
        this.exceptionResolver = exceptionResolver;
    }

    @Override
    public void commence(
            HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) {
        Reason reason = ApiKeyAuthenticationFilter.failureOf(request);
        log.warn("Authentication failed: reason={}", reason);
        audit.recordRejection(auditContexts.of(request), AuditAction.AUTH_FAILED, null, null, reason.name());
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        exceptionResolver.resolveException(
                request, response, null, new ErrorResponseException(HttpStatus.UNAUTHORIZED));
    }
}
