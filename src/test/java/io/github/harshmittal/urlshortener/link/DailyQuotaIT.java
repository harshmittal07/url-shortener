package io.github.harshmittal.urlshortener.link;

import static io.github.harshmittal.urlshortener.support.ApiCalls.bearer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.github.harshmittal.urlshortener.shared.ratelimit.domain.SettableClock;
import io.github.harshmittal.urlshortener.support.ApiCalls;
import io.github.harshmittal.urlshortener.support.ApiCalls.Owner;
import io.github.harshmittal.urlshortener.support.AuditRows;
import io.github.harshmittal.urlshortener.support.IntegrationTest;
import io.github.harshmittal.urlshortener.support.RequestBodies;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Spec 02: the daily link quota per API key (S-09), set to 3 here, with a per-minute limit high
 * enough not to interfere. Time comes from a settable clock, so day boundaries are exact.
 */
@IntegrationTest
@AutoConfigureMockMvc
@Import(DailyQuotaIT.QuotaClock.class)
@TestPropertySource(
        properties = {"url-shortener.link-daily-quota=3", "url-shortener.link-create-limit-per-minute=1000"})
class DailyQuotaIT {

    private static final int QUOTA = 3;
    private static final Instant MIDDAY = Instant.parse("2026-10-07T12:00:00Z");
    private static final SettableClock CLOCK = new SettableClock(MIDDAY);

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient jdbc;

    @BeforeEach
    void resetClock() {
        CLOCK.set(MIDDAY);
    }

    @Test
    @DisplayName("AC1, AC10 (spec 02), S-09, S-10: over the quota: 429 quota-exceeded, no link, one RATE_LIMITED row")
    void refusesOverTheQuota() throws Exception {
        Owner owner = ApiCalls.newOwner(mvc);
        useQuota(mvc, owner, QUOTA);
        String requestId = "q-ac10-" + UUID.randomUUID();

        ApiCalls.createLink(mvc, owner.key(), "https://example.com/over", requestId)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("quota-exceeded"));

        assertThat(linksOf(owner)).isEqualTo(QUOTA);
        assertThat(AuditRows.forRequest(jdbc, requestId)).singleElement().satisfies(event -> {
            assertThat(event.get("action")).isEqualTo("RATE_LIMITED");
            assertThat(event.get("outcome")).isEqualTo("REJECTED");
            assertThat(event.get("actor_key_id")).isEqualTo(owner.keyId());
            assertThat(event.get("resource_type")).isEqualTo("LINK");
            assertThat(event.get("resource_id")).isNull();
            assertThat(event.get("reason_code")).isEqualTo("DAILY_QUOTA");
            assertThat(event.get("client_ip_hash")).isNotNull();
        });
    }

    @Test
    @DisplayName(
            "AC8 (spec 02), S-16: the body has exactly the problem fields, and Retry-After is 7200 at 22:00:00.250")
    void bodyAndRetryAfter() throws Exception {
        CLOCK.set(Instant.parse("2026-10-07T22:00:00.250Z"));
        Owner owner = ApiCalls.newOwner(mvc);
        useQuota(mvc, owner, QUOTA);
        String requestId = "q-ac8-" + UUID.randomUUID();

        String body = ApiCalls.createLink(mvc, owner.key(), "https://example.com/over", requestId)
                .andExpect(status().isTooManyRequests())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string("Retry-After", "7200"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        Map<String, Object> problem = JsonPath.read(body, "$");
        assertThat(problem)
                .containsOnlyKeys("type", "title", "status", "instance", "code", "requestId")
                .containsEntry("type", "urn:url-shortener:problem:quota-exceeded")
                .containsEntry("title", "Too Many Requests")
                .containsEntry("status", 429)
                .containsEntry("instance", "/api/links")
                .containsEntry("code", "quota-exceeded")
                .containsEntry("requestId", requestId);
    }

    @Test
    @DisplayName("AC3 (spec 02), S-09: deleted links still count toward the quota")
    void deletedLinksStillCount() throws Exception {
        Owner owner = ApiCalls.newOwner(mvc);
        for (String code : useQuota(mvc, owner, QUOTA)) {
            mvc.perform(delete("/api/links/" + code).header("Authorization", bearer(owner.key())))
                    .andExpect(status().isNoContent());
        }

        create(mvc, owner)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("quota-exceeded"));
    }

    @Test
    @DisplayName("AC4 (spec 02): attempts rejected with 413, url-rejected or validation-failed do not count")
    void rejectedAttemptsDoNotCount() throws Exception {
        Owner owner = ApiCalls.newOwner(mvc);
        mvc.perform(post("/api/links")
                        .header("Authorization", bearer(owner.key()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(RequestBodies.createLinkOfSize("https://example.com/big", 9000)))
                .andExpect(status().isPayloadTooLarge());
        ApiCalls.createLink(mvc, owner.key(), "javascript:alert(1)", "q-ac4-" + UUID.randomUUID())
                .andExpect(jsonPath("$.code").value("url-rejected"));
        ApiCalls.createLink(mvc, owner.key(), "http://127.0.0.1/", "q-ac4-" + UUID.randomUUID())
                .andExpect(jsonPath("$.code").value("url-rejected"));
        postBody(mvc, owner, "{}").andExpect(jsonPath("$.code").value("validation-failed"));

        useQuota(mvc, owner, QUOTA);
        create(mvc, owner).andExpect(jsonPath("$.code").value("quota-exceeded"));
    }

    @Test
    @DisplayName("AC6, AC9 (spec 02): at 23:59:59.999999 UTC refused with Retry-After 1; at 00:00:00 UTC allowed")
    void dayEndsAtMidnightUtc() throws Exception {
        CLOCK.set(Instant.parse("2026-10-07T23:59:59.999999Z"));
        Owner owner = ApiCalls.newOwner(mvc);
        useQuota(mvc, owner, QUOTA);

        create(mvc, owner)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("quota-exceeded"))
                .andExpect(header().string("Retry-After", "1"));

        CLOCK.set(Instant.parse("2026-10-08T00:00:00Z"));
        create(mvc, owner).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("AC7 (spec 02), S-08: one key using its quota does not stop another key")
    void quotaIsPerKey() throws Exception {
        Owner exhausted = ApiCalls.newOwner(mvc);
        Owner other = ApiCalls.newOwner(mvc);
        useQuota(mvc, exhausted, QUOTA);
        create(mvc, exhausted).andExpect(jsonPath("$.code").value("quota-exceeded"));

        create(mvc, other).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("AC13 (spec 02): body validation comes before the quota")
    void validationBeforeQuota() throws Exception {
        Owner owner = ApiCalls.newOwner(mvc);
        useQuota(mvc, owner, QUOTA);

        postBody(mvc, owner, "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation-failed"));
    }

    @Test
    @DisplayName("AC14 (spec 02): the quota comes before the URL policy; no URL_REJECTED is written")
    void quotaBeforeUrlPolicy() throws Exception {
        Owner owner = ApiCalls.newOwner(mvc);
        useQuota(mvc, owner, QUOTA);
        String requestId = "q-ac14-" + UUID.randomUUID();

        ApiCalls.createLink(mvc, owner.key(), "javascript:alert(1)", requestId)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("quota-exceeded"));

        assertThat(AuditRows.forRequest(jdbc, requestId))
                .extracting(event -> event.get("action"))
                .containsExactly("RATE_LIMITED");
    }

    @Test
    @DisplayName("AC15 (spec 02): the body cap and authentication come before the quota")
    void bodyCapAndAuthenticationBeforeQuota() throws Exception {
        Owner owner = ApiCalls.newOwner(mvc);
        useQuota(mvc, owner, QUOTA);

        mvc.perform(post("/api/links")
                        .header("Authorization", bearer(owner.key()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(RequestBodies.createLinkOfSize("https://example.com/big", 9000)))
                .andExpect(status().isPayloadTooLarge());
        mvc.perform(post("/api/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetUrl\":\"https://example.com/anon\"}"))
                .andExpect(status().isUnauthorized());
    }

    /** Per-minute limit 3 and quota 2, so both limits can be reached in one test. */
    @Nested
    @TestPropertySource(
            properties = {"url-shortener.link-daily-quota=2", "url-shortener.link-create-limit-per-minute=3"})
    class CheckOrder {

        @Autowired
        MockMvc limitedMvc;

        @Autowired
        JdbcClient limitedJdbc;

        @Test
        @DisplayName("AC12, AC16 (spec 02): a quota refusal uses a per-minute token; over both, rate-limited wins")
        void perMinuteLimitComesFirst() throws Exception {
            Owner owner = ApiCalls.newOwner(limitedMvc);
            useQuota(limitedMvc, owner, 2);
            create(limitedMvc, owner).andExpect(jsonPath("$.code").value("quota-exceeded"));
            String requestId = "q-ac12-" + UUID.randomUUID();

            ApiCalls.createLink(limitedMvc, owner.key(), "https://example.com/both", requestId)
                    .andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.code").value("rate-limited"));

            assertThat(AuditRows.forRequest(limitedJdbc, requestId))
                    .singleElement()
                    .satisfies(event -> assertThat(event.get("reason_code")).isEqualTo("CREATE_LIMIT"));
        }

        @Test
        @DisplayName("AC4 (spec 02): attempts refused by the per-minute limit do not count toward the quota")
        void perMinuteRefusalsDoNotCount() throws Exception {
            Owner owner = ApiCalls.newOwner(limitedMvc);
            for (int attempt = 0; attempt < 3; attempt++) {
                ApiCalls.createLink(limitedMvc, owner.key(), "javascript:alert(1)", "q-ac4-" + UUID.randomUUID())
                        .andExpect(jsonPath("$.code").value("url-rejected"));
            }
            create(limitedMvc, owner).andExpect(jsonPath("$.code").value("rate-limited"));

            CLOCK.advance(Duration.ofSeconds(61));
            useQuota(limitedMvc, owner, 2);
            create(limitedMvc, owner).andExpect(jsonPath("$.code").value("quota-exceeded"));
        }
    }

    /** Creates {@code count} links, each expected to succeed, and returns their codes. */
    private static List<String> useQuota(MockMvc mvc, Owner owner, int count) throws Exception {
        List<String> codes = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String body = create(mvc, owner)
                    .andExpect(status().isCreated())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            codes.add(JsonPath.read(body, "$.code"));
        }
        return codes;
    }

    private static ResultActions create(MockMvc mvc, Owner owner) throws Exception {
        return ApiCalls.createLink(
                mvc, owner.key(), "https://example.com/" + UUID.randomUUID(), "q-" + UUID.randomUUID());
    }

    private static ResultActions postBody(MockMvc mvc, Owner owner, String body) throws Exception {
        return mvc.perform(post("/api/links")
                .header("Authorization", bearer(owner.key()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private int linksOf(Owner owner) {
        return jdbc.sql("SELECT count(*) FROM link.links WHERE owner_key_id = :owner")
                .param("owner", owner.keyId())
                .query(Integer.class)
                .single();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class QuotaClock {
        @Bean
        @Primary
        Clock quotaClock() {
            return CLOCK;
        }
    }
}
