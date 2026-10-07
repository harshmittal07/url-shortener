package io.github.harshmittal.urlshortener.redirect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.jayway.jsonpath.JsonPath;
import io.github.harshmittal.urlshortener.link.adapter.out.persistence.JdbcLinkRepository;
import io.github.harshmittal.urlshortener.link.domain.Link;
import io.github.harshmittal.urlshortener.link.domain.LinkStatus;
import io.github.harshmittal.urlshortener.link.domain.ShortCode;
import io.github.harshmittal.urlshortener.link.domain.TestCodes;
import io.github.harshmittal.urlshortener.support.ApiCalls;
import io.github.harshmittal.urlshortener.support.IntegrationTest;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
@AutoConfigureMockMvc
class RedirectIT {

    private static final String TARGET = "https://example.com/redirect?q=1#frag";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient jdbc;

    private String activeCode;

    @BeforeEach
    void givenActiveLink() throws Exception {
        activeCode = ApiCalls.newLinkCode(mvc, ApiCalls.newOwnerKey(mvc), TARGET);
    }

    @Test
    @DisplayName("AC23, D5: an active code redirects with 302, Location = stored target, Cache-Control no-store")
    void redirectsActiveCode() throws Exception {
        MockHttpServletResponse response =
                mvc.perform(get("/" + activeCode)).andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(302);
        assertThat(response.getHeader("Location")).isEqualTo(TARGET);
        assertThat(response.getHeader("Cache-Control")).contains("no-store");
    }

    @Test
    @DisplayName("Edge case: a query string on the short URL is ignored")
    void ignoresQueryString() throws Exception {
        MockHttpServletResponse response =
                mvc.perform(get("/" + activeCode + "?utm=x")).andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(302);
        assertThat(response.getHeader("Location")).isEqualTo(TARGET);
    }

    @Test
    @DisplayName("AC25, R14: a successful redirect writes no row")
    void redirectWritesNothing() throws Exception {
        long linksBefore = linkRows();
        long auditBefore = auditRows();

        mvc.perform(get("/" + activeCode));

        assertThat(linkRows()).isEqualTo(linksBefore);
        assertThat(auditRows()).isEqualTo(auditBefore);
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {"unknown", "deleted", "short", "too-long", "non-base62", "trailing-slash"})
    @DisplayName("AC24, R13: unknown, deleted and malformed codes all get the same 404 not-found")
    void notFoundResponsesAreIdentical(String kind) throws Exception {
        MockHttpServletResponse reference =
                mvc.perform(get("/" + TestCodes.random().value())).andReturn().getResponse();
        MockHttpServletResponse response =
                mvc.perform(get(pathFor(kind))).andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(404).isEqualTo(reference.getStatus());
        assertThat(response.getContentType()).isEqualTo(reference.getContentType());
        assertThat(response.getHeaderNames()).containsExactlyInAnyOrderElementsOf(reference.getHeaderNames());
        assertThat(comparableBody(response)).isEqualTo(comparableBody(reference));
        assertThat((String) JsonPath.read(response.getContentAsString(), "$.code"))
                .isEqualTo("not-found");
    }

    private String pathFor(String kind) {
        return switch (kind) {
            case "unknown" -> "/" + TestCodes.random().value();
            case "deleted" -> "/" + deletedCode().value();
            case "short" -> "/abc";
            case "too-long" -> "/abcdefgh";
            case "non-base62" -> "/abc-efg";
            case "trailing-slash" -> "/" + activeCode + "/";
            default -> throw new IllegalArgumentException(kind);
        };
    }

    private ShortCode deletedCode() {
        ShortCode code = TestCodes.random();
        new JdbcLinkRepository(jdbc)
                .insertIfCodeFree(new Link(
                        UUID.randomUUID(),
                        code,
                        "https://example.com/deleted",
                        UUID.randomUUID(),
                        LinkStatus.DELETED,
                        Instant.parse("2026-10-07T00:00:00Z")));
        return code;
    }

    /** The body without per-request fields. */
    private static Map<String, Object> comparableBody(MockHttpServletResponse response) throws Exception {
        Map<String, Object> body = JsonPath.parse(response.getContentAsString()).json();
        body.remove("requestId");
        body.remove("instance");
        return body;
    }

    private long linkRows() {
        return jdbc.sql("SELECT count(*) FROM link.links").query(Long.class).single();
    }

    private long auditRows() {
        return jdbc.sql("SELECT count(*) FROM audit.audit_events")
                .query(Long.class)
                .single();
    }
}
