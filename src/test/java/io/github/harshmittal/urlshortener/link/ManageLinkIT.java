package io.github.harshmittal.urlshortener.link;

import static io.github.harshmittal.urlshortener.support.ApiCalls.bearer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.jayway.jsonpath.JsonPath;
import io.github.harshmittal.urlshortener.link.domain.TestCodes;
import io.github.harshmittal.urlshortener.support.ApiCalls;
import io.github.harshmittal.urlshortener.support.AuditRows;
import io.github.harshmittal.urlshortener.support.IntegrationTest;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Owner-scoped read and soft delete of links (R9–R11). */
@IntegrationTest
@AutoConfigureMockMvc
class ManageLinkIT {

    private static final String TARGET = "https://example.com/manage?q=1";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient jdbc;

    private String ownerKey;
    private String code;

    @BeforeEach
    void givenOwnLink() throws Exception {
        ownerKey = ApiCalls.newOwnerKey(mvc);
        code = ApiCalls.newLinkCode(mvc, ownerKey, TARGET);
    }

    @Test
    @DisplayName("AC18, R9: an owner reads its own active link")
    void ownerReadsOwnLink() throws Exception {
        MockHttpServletResponse response = call("GET", code, ownerKey, "ac18-" + UUID.randomUUID());

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        String body = response.getContentAsString();
        assertThat((String) JsonPath.read(body, "$.code")).isEqualTo(code);
        assertThat((String) JsonPath.read(body, "$.shortUrl")).isEqualTo("https://sho.rt/" + code);
        assertThat((String) JsonPath.read(body, "$.targetUrl")).isEqualTo(TARGET);
        assertThat((String) JsonPath.read(body, "$.status")).isEqualTo("ACTIVE");
        assertThat((String) JsonPath.read(body, "$.createdAt")).isNotBlank();
    }

    @Test
    @DisplayName("AC19, R10: DELETE soft-deletes: 204, the row stays with status DELETED, and LINK_DELETED is written")
    void ownerSoftDeletesOwnLink() throws Exception {
        String requestId = "ac19-" + UUID.randomUUID();

        MockHttpServletResponse response = call("DELETE", code, ownerKey, requestId);

        assertThat(response.getStatus()).isEqualTo(204);
        assertThat(response.getContentAsString()).isEmpty();
        assertThat(statusOf(code)).isEqualTo("DELETED");
        assertThat(AuditRows.forRequest(jdbc, requestId)).singleElement().satisfies(event -> {
            assertThat(event.get("action")).isEqualTo("LINK_DELETED");
            assertThat(event.get("outcome")).isEqualTo("SUCCESS");
            assertThat(event.get("resource_type")).isEqualTo("LINK");
            assertThat(event.get("resource_id")).isEqualTo(code);
        });
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"GET", "DELETE"})
    @DisplayName(
            "AC20, S-08: another key gets the same 404 as an unknown code, the link is unchanged, ACCESS_DENIED is written")
    void otherOwnerGetsUnknownCodeResponse(String method) throws Exception {
        String otherKey = ApiCalls.newOwnerKey(mvc);
        String requestId = "ac20-" + UUID.randomUUID();

        MockHttpServletResponse crossOwner = call(method, code, otherKey, requestId);
        MockHttpServletResponse unknown =
                call(method, TestCodes.random().value(), otherKey, "ac20-ref-" + UUID.randomUUID());

        assertSameNotFound(crossOwner, unknown);
        assertThat(statusOf(code)).isEqualTo("ACTIVE");
        assertThat(AuditRows.forRequest(jdbc, requestId)).singleElement().satisfies(event -> {
            assertThat(event.get("action")).isEqualTo("ACCESS_DENIED");
            assertThat(event.get("outcome")).isEqualTo("REJECTED");
            assertThat(event.get("reason_code")).isEqualTo("NOT_OWNER");
            assertThat(event.get("resource_id")).isEqualTo(code);
        });
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"GET", "DELETE"})
    @DisplayName("AC21, R11: a deleted link gets the same 404 as an unknown code, and no second LINK_DELETED")
    void deletedLinkGetsUnknownCodeResponse(String method) throws Exception {
        assertThat(call("DELETE", code, ownerKey, "setup-" + UUID.randomUUID()).getStatus())
                .isEqualTo(204);
        String requestId = "ac21-" + UUID.randomUUID();

        MockHttpServletResponse deleted = call(method, code, ownerKey, requestId);
        MockHttpServletResponse unknown =
                call(method, TestCodes.random().value(), ownerKey, "ac21-ref-" + UUID.randomUUID());

        assertSameNotFound(deleted, unknown);
        assertThat(AuditRows.forRequest(jdbc, requestId)).isEmpty();
        assertThat(linkDeletedEvents(code)).isEqualTo(1);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"GET", "DELETE"})
    @DisplayName("R11, R13: a malformed code gets the same 404 as an unknown code")
    void malformedCodeGetsUnknownCodeResponse(String method) throws Exception {
        MockHttpServletResponse malformed = call(method, "abcdefgh", ownerKey, "r11-" + UUID.randomUUID());
        MockHttpServletResponse unknown =
                call(method, TestCodes.random().value(), ownerKey, "r11-ref-" + UUID.randomUUID());

        assertSameNotFound(malformed, unknown);
    }

    private MockHttpServletResponse call(String method, String linkCode, String key, String requestId)
            throws Exception {
        MockHttpServletRequestBuilder request = switch (method) {
            case "GET" -> get("/api/links/" + linkCode);
            case "DELETE" -> delete("/api/links/" + linkCode);
            default -> throw new IllegalArgumentException(method);
        };
        return mvc.perform(request.header("Authorization", bearer(key)).header("X-Request-Id", requestId))
                .andReturn()
                .getResponse();
    }

    /**
     * Status, headers and body must match byte for byte, except the per-request values: the
     * X-Request-Id header, the body's requestId and the request path in instance (plan §12).
     */
    private static void assertSameNotFound(MockHttpServletResponse actual, MockHttpServletResponse reference)
            throws Exception {
        assertThat(actual.getStatus()).isEqualTo(404).isEqualTo(reference.getStatus());
        assertThat(comparableHeaders(actual)).isEqualTo(comparableHeaders(reference));
        assertThat(comparableBody(actual)).isEqualTo(comparableBody(reference));
        assertThat((String) JsonPath.read(actual.getContentAsString(), "$.code"))
                .isEqualTo("not-found");
    }

    private static Map<String, Object> comparableHeaders(MockHttpServletResponse response) {
        Map<String, Object> headers = new TreeMap<>();
        for (String name : response.getHeaderNames()) {
            headers.put(name, name.equalsIgnoreCase("X-Request-Id") ? "<id>" : response.getHeaderValues(name));
        }
        return headers;
    }

    private static String comparableBody(MockHttpServletResponse response) throws Exception {
        String body = response.getContentAsString();
        String requestId = JsonPath.read(body, "$.requestId");
        body = body.replace(requestId, "<requestId>");
        if (JsonPath.parse(body).read("$") instanceof Map<?, ?> fields
                && fields.get("instance") instanceof String path) {
            body = body.replace(path, "<instance>");
        }
        return body;
    }

    private String statusOf(String linkCode) {
        return jdbc.sql("SELECT status FROM link.links WHERE code = :code")
                .param("code", linkCode)
                .query(String.class)
                .single();
    }

    private long linkDeletedEvents(String linkCode) {
        return jdbc.sql("SELECT count(*) FROM audit.audit_events WHERE action = 'LINK_DELETED' AND resource_id = :code")
                .param("code", linkCode)
                .query(Long.class)
                .single();
    }
}
