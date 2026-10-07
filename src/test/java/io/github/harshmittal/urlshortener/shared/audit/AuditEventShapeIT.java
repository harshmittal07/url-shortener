package io.github.harshmittal.urlshortener.shared.audit;

import static io.github.harshmittal.urlshortener.support.ApiCalls.bearer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import io.github.harshmittal.urlshortener.support.ApiCalls;
import io.github.harshmittal.urlshortener.support.ApiCalls.Owner;
import io.github.harshmittal.urlshortener.support.AuditRows;
import io.github.harshmittal.urlshortener.support.FakeKeys;
import io.github.harshmittal.urlshortener.support.IntegrationTest;
import io.github.harshmittal.urlshortener.support.TestAdminKey;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

/** AC32: every audited action in T2 writes a complete row with nothing sensitive in it. */
@IntegrationTest
@AutoConfigureMockMvc
@Import(AuditEventShapeIT.FixedClock.class)
class AuditEventShapeIT {

    static final Instant FIXED_NOW = Instant.parse("2026-10-07T12:34:56.789012Z");
    private static final String MOCK_CLIENT_IP = "127.0.0.1";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient jdbc;

    @Test
    @DisplayName("AC32, S-10: API_KEY_CREATED has every field and no key")
    void apiKeyCreated() throws Exception {
        String requestId = newRequestId();
        String body = mvc.perform(post("/api/keys")
                        .header("Authorization", bearer(TestAdminKey.key()))
                        .header("X-Request-Id", requestId))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String issuedKey = com.jayway.jsonpath.JsonPath.read(body, "$.key");

        Map<String, Object> row = onlyRow(requestId);
        assertComplete(row, requestId, "API_KEY_CREATED", "SUCCESS");
        assertThat(row.get("actor_key_id")).isEqualTo(new UUID(0L, 0L));
        assertThat(row.get("resource_type")).isEqualTo("API_KEY");
        assertNothingSensitive(row, issuedKey, TestAdminKey.key());
    }

    @Test
    @DisplayName("AC32, S-10: LINK_CREATED has every field and no target URL")
    void linkCreated() throws Exception {
        Owner owner = ApiCalls.newOwner(mvc);
        String requestId = newRequestId();
        String target = "https://example.com/ac32/" + requestId;

        ApiCalls.createLink(mvc, owner.key(), target, requestId);

        Map<String, Object> row = onlyRow(requestId);
        assertComplete(row, requestId, "LINK_CREATED", "SUCCESS");
        assertThat(row.get("actor_key_id")).isEqualTo(owner.keyId());
        assertThat(row.get("resource_type")).isEqualTo("LINK");
        assertThat((String) row.get("resource_id")).matches("[0-9A-Za-z]{7}");
        assertNothingSensitive(row, owner.key(), target);
    }

    @Test
    @DisplayName("AC32, S-10: AUTH_FAILED has every field, a null actor and no presented key")
    void authFailed() throws Exception {
        String requestId = newRequestId();
        String presented = FakeKeys.withPrefix("UnknownPref9", 'z');

        mvc.perform(post("/api/keys").header("Authorization", bearer(presented)).header("X-Request-Id", requestId));

        Map<String, Object> row = onlyRow(requestId);
        assertComplete(row, requestId, "AUTH_FAILED", "REJECTED");
        assertThat(row.get("actor_key_id")).isNull();
        assertThat(row.get("reason_code")).isEqualTo("UNKNOWN");
        assertNothingSensitive(row, presented);
    }

    @Test
    @DisplayName("AC32, S-10: ACCESS_DENIED has every field and the caller's key ID")
    void accessDenied() throws Exception {
        Owner owner = ApiCalls.newOwner(mvc);
        String requestId = newRequestId();

        mvc.perform(
                post("/api/keys").header("Authorization", bearer(owner.key())).header("X-Request-Id", requestId));

        Map<String, Object> row = onlyRow(requestId);
        assertComplete(row, requestId, "ACCESS_DENIED", "REJECTED");
        assertThat(row.get("actor_key_id")).isEqualTo(owner.keyId());
        assertNothingSensitive(row, owner.key());
    }

    @Test
    @DisplayName("AC32, S-10: URL_REJECTED has every field, the reason, and no target URL")
    void urlRejected() throws Exception {
        Owner owner = ApiCalls.newOwner(mvc);
        String requestId = newRequestId();
        String target = "javascript:alert(" + requestId.hashCode() + ")";

        ApiCalls.createLink(mvc, owner.key(), target, requestId);

        Map<String, Object> row = onlyRow(requestId);
        assertComplete(row, requestId, "URL_REJECTED", "REJECTED");
        assertThat(row.get("reason_code")).isEqualTo("SCHEME_NOT_ALLOWED");
        assertNothingSensitive(row, owner.key(), target);
    }

    @Test
    @DisplayName("AC32: a public redirect is not audited")
    void redirectIsNotAudited() throws Exception {
        String code = ApiCalls.newLinkCode(mvc, ApiCalls.newOwnerKey(mvc), "https://example.com/r");
        String requestId = newRequestId();

        mvc.perform(get("/" + code).header("X-Request-Id", requestId));

        assertThat(AuditRows.forRequest(jdbc, requestId)).isEmpty();
    }

    private Map<String, Object> onlyRow(String requestId) {
        var rows = AuditRows.forRequest(jdbc, requestId);
        assertThat(rows).hasSize(1);
        return rows.getFirst();
    }

    private static void assertComplete(Map<String, Object> row, String requestId, String action, String outcome) {
        assertThat(row)
                .containsKeys(
                        "id",
                        "occurred_at",
                        "request_id",
                        "actor_key_id",
                        "action",
                        "resource_type",
                        "resource_id",
                        "outcome",
                        "reason_code",
                        "client_ip_hash");
        assertThat(row.get("id")).isInstanceOf(UUID.class);
        assertThat(((Timestamp) row.get("occurred_at")).toInstant())
                .as("occurred_at comes from the injected clock")
                .isEqualTo(FIXED_NOW);
        assertThat(row.get("request_id")).isEqualTo(requestId);
        assertThat(row.get("action")).isEqualTo(action);
        assertThat(row.get("outcome")).isEqualTo(outcome);
        assertThat((String) row.get("client_ip_hash")).matches("[0-9a-f]{64}");
    }

    private static void assertNothingSensitive(Map<String, Object> row, String... secrets) {
        for (Object value : row.values()) {
            if (value == null) {
                continue;
            }
            String text = value.toString();
            assertThat(text).doesNotContain(MOCK_CLIENT_IP).doesNotContain("://");
            for (String secret : secrets) {
                assertThat(text).doesNotContain(secret);
            }
        }
    }

    private static String newRequestId() {
        return "ac32-" + UUID.randomUUID();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClock {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
        }
    }
}
