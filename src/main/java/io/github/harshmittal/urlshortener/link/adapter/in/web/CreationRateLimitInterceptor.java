package io.github.harshmittal.urlshortener.link.adapter.in.web;

import io.github.harshmittal.urlshortener.shared.audit.domain.AuditAction;
import io.github.harshmittal.urlshortener.shared.audit.domain.AuditContext;
import io.github.harshmittal.urlshortener.shared.audit.domain.AuditTrail;
import io.github.harshmittal.urlshortener.shared.ratelimit.domain.RateLimitDecision;
import io.github.harshmittal.urlshortener.shared.ratelimit.domain.RateLimiter;
import io.github.harshmittal.urlshortener.shared.web.RequestAuditContexts;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Counts every authenticated {@code POST /api/links} against the caller's per-minute limit (R15).
 * It runs after authentication and before the body is bound, so attempts later rejected by
 * validation or the URL policy count too. A {@code 413} never reaches it: the body limit is an
 * earlier servlet filter. Over the limit: a {@code RATE_LIMITED} audit event and {@code 429} (R16).
 */
public final class CreationRateLimitInterceptor implements HandlerInterceptor {

    private static final String RESOURCE_TYPE = "LINK";
    /** RATE_LIMITED reason for the per-key creation limit; spec 02's per-IP limit uses its own (A13). */
    static final String CREATE_LIMIT = "CREATE_LIMIT";

    private static final Logger log = LoggerFactory.getLogger(CreationRateLimitInterceptor.class);

    private final RateLimiter rateLimiter;
    private final AuditTrail audit;
    private final RequestAuditContexts auditContexts;

    public CreationRateLimitInterceptor(RateLimiter rateLimiter, AuditTrail audit, RequestAuditContexts auditContexts) {
        this.rateLimiter = rateLimiter;
        this.audit = audit;
        this.auditContexts = auditContexts;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!HttpMethod.POST.matches(request.getMethod())) {
            return true;
        }
        AuditContext context = auditContexts.of(request);
        // The security configuration admits only owners here; without a key ID, fail closed.
        UUID keyId = Objects.requireNonNull(context.actorKeyId(), "link creation requires an authenticated owner");
        if (rateLimiter.tryConsume(keyId.toString()) instanceof RateLimitDecision.Rejected rejected) {
            log.warn("Rate limited: reason={} actorKeyId={}", CREATE_LIMIT, keyId);
            audit.recordRejection(context, AuditAction.RATE_LIMITED, RESOURCE_TYPE, null, CREATE_LIMIT);
            throw new CreationRateLimitedException(rejected.retryAfter());
        }
        return true;
    }
}
