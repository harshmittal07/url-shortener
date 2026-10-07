package io.github.harshmittal.urlshortener.shared.identity.adapter.in.web;

import io.github.harshmittal.urlshortener.shared.audit.domain.AuditAction;
import io.github.harshmittal.urlshortener.shared.audit.domain.AuditTrail;
import io.github.harshmittal.urlshortener.shared.web.RequestAuditContexts;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.servlet.HandlerExceptionResolver;

/** A valid key with the wrong role: audits {@code ACCESS_DENIED} and answers {@code 403} (R4, A3). */
public final class ProblemAccessDeniedHandler implements AccessDeniedHandler {

    static final String WRONG_ROLE = "WRONG_ROLE";

    private static final Logger log = LoggerFactory.getLogger(ProblemAccessDeniedHandler.class);

    private final AuditTrail audit;
    private final RequestAuditContexts auditContexts;
    private final HandlerExceptionResolver exceptionResolver;

    public ProblemAccessDeniedHandler(
            AuditTrail audit, RequestAuditContexts auditContexts, HandlerExceptionResolver exceptionResolver) {
        this.audit = audit;
        this.auditContexts = auditContexts;
        this.exceptionResolver = exceptionResolver;
    }

    @Override
    public void handle(
            HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException) {
        var context = auditContexts.of(request);
        log.warn("Access denied: reason={} actorKeyId={}", WRONG_ROLE, context.actorKeyId());
        audit.recordRejection(context, AuditAction.ACCESS_DENIED, null, null, WRONG_ROLE);
        exceptionResolver.resolveException(request, response, null, new ErrorResponseException(HttpStatus.FORBIDDEN));
    }
}
