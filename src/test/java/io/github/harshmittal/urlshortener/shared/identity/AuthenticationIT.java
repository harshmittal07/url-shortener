package io.github.harshmittal.urlshortener.shared.identity;

import static io.github.harshmittal.urlshortener.support.ApiCalls.bearer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.harshmittal.urlshortener.support.ApiCalls;
import io.github.harshmittal.urlshortener.support.AuditRows;
import io.github.harshmittal.urlshortener.support.FakeKeys;
import io.github.harshmittal.urlshortener.support.IntegrationTest;
import io.github.harshmittal.urlshortener.support.Sha256;
import io.github.harshmittal.urlshortener.support.TestAdminKey;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@IntegrationTest
@AutoConfigureMockMvc
class AuthenticationIT {

    private static final String UNKNOWN_KEY = FakeKeys.withPrefix("UnknownPref1", 'u');
    // Each test JVM gets a fresh database, so fixed prefixes are unique.
    private static final String REVOKED_KEY = FakeKeys.withPrefix("RevokedPref1", 'r');

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient jdbc;

    static Stream<Arguments> badCredentials() {
        return Stream.of(
                Arguments.of("missing", null, "MISSING"),
                Arguments.of("malformed", "Bearer not-a-key", "MALFORMED"),
                Arguments.of("other scheme", "Basic Zm9v", "MALFORMED"),
                Arguments.of("unknown", bearer(UNKNOWN_KEY), "UNKNOWN"),
                Arguments.of("revoked", bearer(REVOKED_KEY), "REVOKED"));
    }

    static Stream<String> apiEndpoints() {
        return Stream.of(
                "POST /api/keys",
                "POST /api/links",
                "GET /api/links",
                "GET /api/links/abcdefg",
                "DELETE /api/links/abcdefg");
    }

    static Stream<Arguments> badCredentialsOnEveryEndpoint() {
        return apiEndpoints()
                .flatMap(endpoint -> badCredentials().map(args -> {
                    Object[] values = args.get();
                    return Arguments.of(endpoint, values[0], values[1], values[2]);
                }));
    }

    @ParameterizedTest(name = "{0} with {1} key")
    @MethodSource("badCredentialsOnEveryEndpoint")
    @DisplayName("AC3, S-07: a missing, malformed, unknown or revoked key gets 401 and an AUTH_FAILED event")
    void rejectsBadCredentials(String endpoint, String label, String authorization, String reason) throws Exception {
        insertRevokedKeyOnce();
        String requestId = "ac3-" + UUID.randomUUID();
        MockHttpServletRequestBuilder request = request(endpoint).header("X-Request-Id", requestId);
        if (authorization != null) {
            request.header("Authorization", authorization);
        }

        mvc.perform(request)
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("unauthorized"))
                .andExpect(jsonPath("$.requestId").value(requestId))
                .andExpect(header().string("WWW-Authenticate", "Bearer"));

        assertThat(AuditRows.forRequest(jdbc, requestId)).singleElement().satisfies(event -> {
            assertThat(event.get("action")).isEqualTo("AUTH_FAILED");
            assertThat(event.get("outcome")).isEqualTo("REJECTED");
            assertThat(event.get("reason_code")).isEqualTo(reason);
            assertThat(event.get("actor_key_id")).isNull();
        });
    }

    @Test
    @DisplayName("AC4, R4: an owner key calling POST /api/keys gets 403 and an ACCESS_DENIED event")
    void ownerCannotIssueKeys() throws Exception {
        String ownerKey = ApiCalls.newOwnerKey(mvc);
        String requestId = "ac4-" + UUID.randomUUID();

        mvc.perform(post("/api/keys").header("Authorization", bearer(ownerKey)).header("X-Request-Id", requestId))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("forbidden"));

        assertThat(AuditRows.forRequest(jdbc, requestId)).singleElement().satisfies(event -> {
            assertThat(event.get("action")).isEqualTo("ACCESS_DENIED");
            assertThat(event.get("reason_code")).isEqualTo("WRONG_ROLE");
            assertThat(event.get("actor_key_id")).isNotNull().isNotEqualTo(new UUID(0L, 0L));
        });
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("linkEndpoints")
    @DisplayName("AC5, A3: the admin key calling a link endpoint gets 403 and an ACCESS_DENIED event")
    void adminCannotUseLinkEndpoints(String endpoint) throws Exception {
        String requestId = "ac5-" + UUID.randomUUID();

        mvc.perform(request(endpoint)
                        .header("Authorization", bearer(TestAdminKey.key()))
                        .header("X-Request-Id", requestId))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("forbidden"));

        assertThat(AuditRows.forRequest(jdbc, requestId)).singleElement().satisfies(event -> {
            assertThat(event.get("action")).isEqualTo("ACCESS_DENIED");
            assertThat(event.get("actor_key_id")).isEqualTo(new UUID(0L, 0L));
        });
    }

    static Stream<Arguments> unclassifiedRoutesWithValidKeys() {
        return Stream.of("GET /api/unclassified", "POST /api/unclassified", "GET /api/keys", "GET /api")
                .flatMap(endpoint -> Stream.of(Arguments.of(endpoint, "owner"), Arguments.of(endpoint, "admin")));
    }

    @ParameterizedTest(name = "{0} with {1} key")
    @MethodSource("unclassifiedRoutesWithValidKeys")
    @DisplayName("R4, S-07: /api/** is default-deny: an unclassified route gets 403 even with a valid key")
    void unclassifiedApiRouteIsDenied(String endpoint, String keyKind) throws Exception {
        String key = keyKind.equals("admin") ? TestAdminKey.key() : ApiCalls.newOwnerKey(mvc);
        String requestId = "deny-" + UUID.randomUUID();

        mvc.perform(request(endpoint).header("Authorization", bearer(key)).header("X-Request-Id", requestId))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("forbidden"))
                .andExpect(jsonPath("$.requestId").value(requestId));

        assertThat(AuditRows.forRequest(jdbc, requestId)).singleElement().satisfies(event -> {
            assertThat(event.get("action")).isEqualTo("ACCESS_DENIED");
            assertThat(event.get("outcome")).isEqualTo("REJECTED");
        });
    }

    static Stream<String> linkEndpoints() {
        return apiEndpoints().filter(endpoint -> endpoint.contains("/api/links"));
    }

    private static MockHttpServletRequestBuilder request(String endpoint) {
        String[] parts = endpoint.split(" ");
        return switch (parts[0]) {
            case "POST" ->
                post(parts[1])
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetUrl\":\"https://example.com\"}");
            case "GET" -> get(parts[1]);
            case "DELETE" -> delete(parts[1]);
            default -> throw new IllegalArgumentException(endpoint);
        };
    }

    private static boolean revokedKeyInserted;

    /** The app user cannot UPDATE api_keys, so the revoked key is inserted already revoked. */
    private void insertRevokedKeyOnce() {
        if (revokedKeyInserted) {
            return;
        }
        jdbc.sql("""
                        INSERT INTO identity.api_keys (id, key_prefix, key_hash, created_at, revoked_at)
                        VALUES (:id, :prefix, :hash, :created, :revoked)
                        """)
                .param("id", UUID.randomUUID())
                .param("prefix", ApiCalls.prefixOf(REVOKED_KEY))
                .param("hash", Sha256.hex(REVOKED_KEY))
                .param("created", Timestamp.from(Instant.parse("2026-10-01T00:00:00Z")))
                .param("revoked", Timestamp.from(Instant.parse("2026-10-02T00:00:00Z")))
                .update();
        revokedKeyInserted = true;
    }
}
