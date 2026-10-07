package io.github.harshmittal.urlshortener.shared.audit;

import static io.github.harshmittal.urlshortener.support.ApiCalls.bearer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.harshmittal.urlshortener.shared.audit.adapter.out.persistence.JdbcAuditSink;
import io.github.harshmittal.urlshortener.shared.audit.domain.AuditEvent;
import io.github.harshmittal.urlshortener.shared.audit.domain.AuditSink;
import io.github.harshmittal.urlshortener.support.ApiCalls;
import io.github.harshmittal.urlshortener.support.ApiCalls.Owner;
import io.github.harshmittal.urlshortener.support.AuditRows;
import io.github.harshmittal.urlshortener.support.FakeKeys;
import io.github.harshmittal.urlshortener.support.IntegrationTest;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * R20: when the audit store fails, every rejection keeps its original response, nothing changes, and
 * a WARN line records the failure without secrets. The creation limit is 2 so the 429 case is short.
 */
@IntegrationTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
@Import(RejectionAuditFailureIT.FailingAudit.class)
@TestPropertySource(properties = "url-shortener.link-create-limit-per-minute=2")
class RejectionAuditFailureIT {

    static final AtomicBoolean FAIL_AUDIT = new AtomicBoolean();

    /** A documentation-range address (RFC 5737) that nothing else in the test output contains. */
    private static final String CLIENT_IP = "203.0.113.77";

    private static final RequestPostProcessor FROM_CLIENT_IP = request -> {
        request.setRemoteAddr(CLIENT_IP);
        return request;
    };

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient jdbc;

    @AfterEach
    void restoreAudit() {
        FAIL_AUDIT.set(false);
    }

    @Test
    @DisplayName("AC33: an unknown key still gets 401 unauthorized when the AUTH_FAILED write fails")
    void authFailureStaysUnauthorized(CapturedOutput output) throws Exception {
        String unknownKey = FakeKeys.withPrefix("ac33unknown0", 'q');
        String requestId = "ac33-401-" + UUID.randomUUID();
        FAIL_AUDIT.set(true);

        mvc.perform(get("/api/links/abcdefg")
                        .header("Authorization", bearer(unknownKey))
                        .header("X-Request-Id", requestId)
                        .with(FROM_CLIENT_IP))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("unauthorized"))
                .andExpect(jsonPath("$.requestId").value(requestId));

        assertThat(AuditRows.forRequest(jdbc, requestId)).isEmpty();
        assertWarnedWithoutSecrets(output, "AUTH_FAILED", requestId, unknownKey);
    }

    @Test
    @DisplayName("AC33: an owner key on POST /api/keys still gets 403 forbidden when ACCESS_DENIED fails")
    void wrongRoleStaysForbidden(CapturedOutput output) throws Exception {
        Owner owner = ApiCalls.newOwner(mvc);
        long keysBefore = keyRows();
        String requestId = "ac33-403-" + UUID.randomUUID();
        FAIL_AUDIT.set(true);

        mvc.perform(post("/api/keys")
                        .header("Authorization", bearer(owner.key()))
                        .header("X-Request-Id", requestId)
                        .with(FROM_CLIENT_IP))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("forbidden"))
                .andExpect(jsonPath("$.requestId").value(requestId));

        assertThat(keyRows()).isEqualTo(keysBefore);
        assertThat(AuditRows.forRequest(jdbc, requestId)).isEmpty();
        assertWarnedWithoutSecrets(output, "ACCESS_DENIED", requestId, owner.key());
    }

    @Test
    @DisplayName("AC33: another owner's delete still gets 404 not-found and changes nothing when ACCESS_DENIED fails")
    void wrongOwnerStaysNotFound(CapturedOutput output) throws Exception {
        Owner owner = ApiCalls.newOwner(mvc);
        Owner intruder = ApiCalls.newOwner(mvc);
        String target = "https://example.com/ac33-owner-" + UUID.randomUUID();
        String code = ApiCalls.newLinkCode(mvc, owner.key(), target);
        String requestId = "ac33-404-" + UUID.randomUUID();
        FAIL_AUDIT.set(true);

        mvc.perform(delete("/api/links/" + code)
                        .header("Authorization", bearer(intruder.key()))
                        .header("X-Request-Id", requestId)
                        .with(FROM_CLIENT_IP))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("not-found"))
                .andExpect(jsonPath("$.requestId").value(requestId));

        String linkStatus = jdbc.sql("SELECT status FROM link.links WHERE code = :code")
                .param("code", code)
                .query(String.class)
                .single();
        assertThat(linkStatus).isEqualTo("ACTIVE");
        assertThat(AuditRows.forRequest(jdbc, requestId)).isEmpty();
        assertWarnedWithoutSecrets(output, "ACCESS_DENIED", requestId, intruder.key(), target);
    }

    @Test
    @DisplayName("AC33: an attempt over the limit still gets 429 rate-limited when the RATE_LIMITED write fails")
    void rateLimitStaysTooManyRequests(CapturedOutput output) throws Exception {
        Owner owner = ApiCalls.newOwner(mvc);
        ApiCalls.newLinkCode(mvc, owner.key(), "https://example.com/ac33-limit-1");
        ApiCalls.newLinkCode(mvc, owner.key(), "https://example.com/ac33-limit-2");
        String target = "https://example.com/ac33-limit-" + UUID.randomUUID();
        String requestId = "ac33-429-" + UUID.randomUUID();
        FAIL_AUDIT.set(true);

        mvc.perform(post("/api/links")
                        .header("Authorization", bearer(owner.key()))
                        .header("X-Request-Id", requestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetUrl\":\"" + target + "\"}")
                        .with(FROM_CLIENT_IP))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("rate-limited"))
                .andExpect(jsonPath("$.requestId").value(requestId));

        assertThat(linksOf(owner)).isEqualTo(2);
        assertThat(AuditRows.forRequest(jdbc, requestId)).isEmpty();
        assertWarnedWithoutSecrets(output, "RATE_LIMITED", requestId, owner.key(), target);
    }

    @Test
    @DisplayName("AC33: a policy rejection still gets 400 url-rejected and stores nothing when URL_REJECTED fails")
    void urlRejectionStaysBadRequest(CapturedOutput output) throws Exception {
        Owner owner = ApiCalls.newOwner(mvc);
        String target = "javascript:alert('ac33-" + UUID.randomUUID() + "')";
        String requestId = "ac33-400-" + UUID.randomUUID();
        FAIL_AUDIT.set(true);

        mvc.perform(post("/api/links")
                        .header("Authorization", bearer(owner.key()))
                        .header("X-Request-Id", requestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetUrl\":\"" + target + "\"}")
                        .with(FROM_CLIENT_IP))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("url-rejected"))
                .andExpect(jsonPath("$.reason").value("SCHEME_NOT_ALLOWED"))
                .andExpect(jsonPath("$.requestId").value(requestId));

        assertThat(linksOf(owner)).isZero();
        assertThat(AuditRows.forRequest(jdbc, requestId)).isEmpty();
        assertWarnedWithoutSecrets(output, "URL_REJECTED", requestId, owner.key(), target);
    }

    private static void assertWarnedWithoutSecrets(
            CapturedOutput output, String action, String requestId, String... secrets) {
        assertThat(output.getAll())
                .containsPattern("WARN.*Audit write failed: action=" + action + " requestId=" + requestId)
                .doesNotContain(CLIENT_IP);
        for (String secret : secrets) {
            assertThat(output.getAll()).doesNotContain(secret);
        }
    }

    private long keyRows() {
        return jdbc.sql("SELECT count(*) FROM identity.api_keys")
                .query(Long.class)
                .single();
    }

    private int linksOf(Owner owner) {
        return jdbc.sql("SELECT count(*) FROM link.links WHERE owner_key_id = :owner")
                .param("owner", owner.keyId())
                .query(Integer.class)
                .single();
    }

    /** Wraps the real sink and fails every event on demand. */
    @TestConfiguration(proxyBeanMethods = false)
    static class FailingAudit {
        @Bean
        @Primary
        AuditSink failingAuditSink(JdbcClient jdbc) {
            AuditSink real = new JdbcAuditSink(jdbc);
            return (AuditEvent event) -> {
                if (FAIL_AUDIT.get()) {
                    throw new IllegalStateException("audit store unavailable");
                }
                real.append(event);
            };
        }
    }
}
