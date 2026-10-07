package io.github.harshmittal.urlshortener.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.DispatcherType;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestIdFilterTest {

    private static final UUID GENERATED = UUID.fromString("00000000-0000-4000-8000-000000000001");

    private final RequestIdFilter filter = new RequestIdFilter(() -> GENERATED);

    @Test
    @DisplayName("AC41: a valid incoming X-Request-Id is reused on the response and in MDC")
    void reusesValidRequestId() throws Exception {
        var request = requestWithId("abc.DEF_123-xyz");
        var response = new MockHttpServletResponse();
        var mdcDuringRequest = new AtomicReference<String>();

        filter.doFilter(request, response, new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                mdcDuringRequest.set(MDC.get("requestId"));
            }
        });

        assertThat(response.getHeader("X-Request-Id")).isEqualTo("abc.DEF_123-xyz");
        assertThat(mdcDuringRequest.get()).isEqualTo("abc.DEF_123-xyz");
        assertThat(RequestIdFilter.requestIdOf(request)).isEqualTo("abc.DEF_123-xyz");
    }

    @Test
    @DisplayName("AC41: an ID of exactly 64 allowed characters is reused")
    void reusesMaximumLengthId() throws Exception {
        String id = "a".repeat(64);
        var response = new MockHttpServletResponse();

        filter.doFilter(requestWithId(id), response, new MockFilterChain());

        assertThat(response.getHeader("X-Request-Id")).isEqualTo(id);
    }

    @ParameterizedTest(name = "[{index}] \"{0}\"")
    @ValueSource(strings = {"", "has space", "line\r\nInjected: header", "<script>", "ünïcode", "a/b"})
    @DisplayName("AC41, A5: an invalid X-Request-Id is replaced with a generated ID")
    void replacesInvalidRequestId(String invalid) throws Exception {
        var response = new MockHttpServletResponse();

        filter.doFilter(requestWithId(invalid), response, new MockFilterChain());

        assertThat(response.getHeader("X-Request-Id")).isEqualTo(GENERATED.toString());
    }

    @Test
    @DisplayName("AC41, A5: an X-Request-Id over 64 characters is replaced with a generated ID")
    void replacesTooLongRequestId() throws Exception {
        var response = new MockHttpServletResponse();

        filter.doFilter(requestWithId("a".repeat(65)), response, new MockFilterChain());

        assertThat(response.getHeader("X-Request-Id")).isEqualTo(GENERATED.toString());
    }

    @Test
    @DisplayName("AC41: a missing X-Request-Id gets a generated ID")
    void generatesMissingRequestId() throws Exception {
        var response = new MockHttpServletResponse();

        filter.doFilter(new MockHttpServletRequest("GET", "/api/links"), response, new MockFilterChain());

        assertThat(response.getHeader("X-Request-Id")).isEqualTo(GENERATED.toString());
    }

    @Test
    @DisplayName("AC41: the request ID is removed from MDC after the request")
    void clearsMdcAfterRequest() throws Exception {
        filter.doFilter(requestWithId("abc"), new MockHttpServletResponse(), new MockFilterChain());

        assertThat(MDC.get("requestId")).isNull();
    }

    @Test
    @DisplayName("AC41: an error dispatch keeps the ID already assigned to the request")
    void errorDispatchKeepsAssignedId() throws Exception {
        var request = new MockHttpServletRequest("GET", "/error");
        var original = new MockHttpServletResponse();
        filter.doFilter(request, original, new MockFilterChain());
        request.setDispatcherType(DispatcherType.ERROR);
        var afterError = new MockHttpServletResponse();

        new RequestIdFilter(() -> UUID.randomUUID()).doFilter(request, afterError, new MockFilterChain());

        assertThat(afterError.getHeader("X-Request-Id")).isEqualTo(GENERATED.toString());
    }

    private static MockHttpServletRequest requestWithId(String id) {
        var request = new MockHttpServletRequest("GET", "/api/links");
        request.addHeader("X-Request-Id", id);
        return request;
    }
}
