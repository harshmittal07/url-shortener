package io.github.harshmittal.urlshortener.shared.web;

import io.github.harshmittal.urlshortener.shared.id.domain.IdGenerator;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Gives every request an ID (R27). A valid incoming {@code X-Request-Id} is reused; anything else is
 * replaced, so a caller cannot inject log lines or headers through it (A5).
 */
public final class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";

    private static final String ATTRIBUTE = RequestIdFilter.class.getName() + ".requestId";
    private static final Pattern VALID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    private final IdGenerator idGenerator;

    public RequestIdFilter(IdGenerator idGenerator) {
        this.idGenerator = idGenerator;
    }

    /** The ID assigned to this request, or {@code null} if the filter has not run. */
    public static String requestIdOf(HttpServletRequest request) {
        return request.getAttribute(ATTRIBUTE) instanceof String id ? id : null;
    }

    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        // Error pages are rendered on a separate dispatch; they must log under the same ID.
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = assignedOrResolved(request);
        request.setAttribute(ATTRIBUTE, requestId);
        response.setHeader(HEADER, requestId);
        MDC.put(MDC_KEY, requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    private String assignedOrResolved(HttpServletRequest request) {
        String assigned = requestIdOf(request);
        if (assigned != null) {
            return assigned;
        }
        String incoming = request.getHeader(HEADER);
        if (incoming != null && VALID.matcher(incoming).matches()) {
            return incoming;
        }
        return idGenerator.newId().toString();
    }
}
