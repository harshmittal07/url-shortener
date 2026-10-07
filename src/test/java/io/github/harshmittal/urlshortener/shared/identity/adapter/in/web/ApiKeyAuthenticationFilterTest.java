package io.github.harshmittal.urlshortener.shared.identity.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.github.harshmittal.urlshortener.shared.identity.domain.Authenticator;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;

class ApiKeyAuthenticationFilterTest {

    private final AtomicReference<Exception> resolved = new AtomicReference<>();
    private final HandlerExceptionResolver resolver = (request, response, handler, ex) -> {
        resolved.set(ex);
        return new ModelAndView();
    };

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName(
            "AC42, S-16: a database failure during the key lookup goes to the error handler (503), never on as a 401")
    void databaseFailureIsResolvedNotPassedOn() {
        var outage = new CannotGetJdbcConnectionException("Failed to obtain JDBC Connection");
        Authenticator failing = token -> {
            throw outage;
        };
        var filter = new ApiKeyAuthenticationFilter(failing, resolver);
        var request = new MockHttpServletRequest("GET", "/api/links/abcdefg");
        request.addHeader("Authorization", "Bearer usk_abcdefghijkl_" + "x".repeat(43));
        var chain = new MockFilterChain();

        assertThatCode(() -> filter.doFilter(request, new MockHttpServletResponse(), chain))
                .doesNotThrowAnyException();

        assertThat(resolved.get()).isSameAs(outage);
        assertThat(chain.getRequest())
                .as("the request must not reach authorization")
                .isNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
