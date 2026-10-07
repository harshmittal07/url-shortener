package io.github.harshmittal.urlshortener.link;

import static io.github.harshmittal.urlshortener.support.ApiCalls.bearer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.harshmittal.urlshortener.support.ApiCalls;
import io.github.harshmittal.urlshortener.support.ApiCalls.Owner;
import io.github.harshmittal.urlshortener.support.AuditRows;
import io.github.harshmittal.urlshortener.support.IntegrationTest;
import io.github.harshmittal.urlshortener.support.RequestBodies;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Per-key creation limit (R15, R16, S-09), set to 3 per minute here for speed. */
@IntegrationTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "url-shortener.link-create-limit-per-minute=3")
class CreationRateLimitIT {

    private static final int LIMIT = 3;

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient jdbc;

    @Test
    @DisplayName(
            "AC26, S-09: the attempt after the limit gets 429 rate-limited with Retry-After, stores no link and is audited")
    void rejectsBeyondTheLimit() throws Exception {
        Owner owner = ApiCalls.newOwner(mvc);
        for (int attempt = 1; attempt <= LIMIT; attempt++) {
            createAllowed(owner).andExpect(status().isCreated());
        }
        String requestId = "ac26-" + UUID.randomUUID();

        MockHttpServletResponse response = ApiCalls.createLink(
                        mvc, owner.key(), "https://example.com/" + requestId, requestId)
                .andExpect(status().isTooManyRequests())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("rate-limited"))
                .andExpect(jsonPath("$.type").value("urn:url-shortener:problem:rate-limited"))
                .andExpect(jsonPath("$.requestId").value(requestId))
                .andExpect(jsonPath("$.detail").doesNotExist())
                .andReturn()
                .getResponse();

        assertThat(response.getHeader("Retry-After")).isNotNull();
        assertThat(Integer.parseInt(response.getHeader("Retry-After"))).isBetween(1, 60);
        assertThat(linksOf(owner)).isEqualTo(LIMIT);
        assertThat(AuditRows.forRequest(jdbc, requestId)).singleElement().satisfies(event -> {
            assertThat(event.get("action")).isEqualTo("RATE_LIMITED");
            assertThat(event.get("outcome")).isEqualTo("REJECTED");
            assertThat(event.get("actor_key_id")).isEqualTo(owner.keyId());
            assertThat(event.get("resource_type")).isEqualTo("LINK");
            assertThat(event.get("resource_id")).isNull();
            assertThat(event.get("reason_code")).isEqualTo("CREATE_LIMIT");
            assertThat(event.get("client_ip_hash")).isNotNull();
        });
    }

    @Test
    @DisplayName("AC28, S-09: one owner exhausting its limit does not stop another owner")
    void ownersHaveSeparateLimits() throws Exception {
        Owner exhausted = ApiCalls.newOwner(mvc);
        Owner other = ApiCalls.newOwner(mvc);
        for (int attempt = 1; attempt <= LIMIT; attempt++) {
            createAllowed(exhausted).andExpect(status().isCreated());
        }
        createAllowed(exhausted).andExpect(status().isTooManyRequests());

        createAllowed(other).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("AC29, S-09: attempts rejected by the URL policy or by validation count toward the limit")
    void rejectedAttemptsCount() throws Exception {
        Owner owner = ApiCalls.newOwner(mvc);
        ApiCalls.createLink(mvc, owner.key(), "javascript:alert(1)", "ac29-" + UUID.randomUUID())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("url-rejected"));
        ApiCalls.createLink(mvc, owner.key(), "http://127.0.0.1/", "ac29-" + UUID.randomUUID())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("url-rejected"));
        mvc.perform(post("/api/links")
                        .header("Authorization", bearer(owner.key()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation-failed"));

        createAllowed(owner).andExpect(status().isTooManyRequests());
        assertThat(linksOf(owner)).isZero();
    }

    @Test
    @DisplayName("R15: a request rejected with 413 does not count toward the limit")
    void oversizedRequestsDoNotCount() throws Exception {
        Owner owner = ApiCalls.newOwner(mvc);
        for (int attempt = 1; attempt <= LIMIT; attempt++) {
            mvc.perform(post("/api/links")
                            .header("Authorization", bearer(owner.key()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(RequestBodies.createLinkOfSize("https://example.com/big", 9000)))
                    .andExpect(status().isPayloadTooLarge());
        }

        for (int attempt = 1; attempt <= LIMIT; attempt++) {
            createAllowed(owner).andExpect(status().isCreated());
        }
    }

    @Nested
    @TestPropertySource(properties = "url-shortener.link-create-limit-per-minute=2")
    class ConfiguredLimit {

        @Autowired
        MockMvc limitedMvc;

        @Test
        @DisplayName("AC27: a configured limit of 2 allows two creations and rejects the third")
        void configuredLimitApplies() throws Exception {
            Owner owner = ApiCalls.newOwner(limitedMvc);

            createAllowed(limitedMvc, owner).andExpect(status().isCreated());
            createAllowed(limitedMvc, owner).andExpect(status().isCreated());
            createAllowed(limitedMvc, owner).andExpect(status().isTooManyRequests());
        }
    }

    private ResultActions createAllowed(Owner owner) throws Exception {
        return createAllowed(mvc, owner);
    }

    private static ResultActions createAllowed(MockMvc mvc, Owner owner) throws Exception {
        return ApiCalls.createLink(
                mvc, owner.key(), "https://example.com/" + UUID.randomUUID(), "rl-" + UUID.randomUUID());
    }

    private int linksOf(Owner owner) {
        return jdbc.sql("SELECT count(*) FROM link.links WHERE owner_key_id = :owner")
                .param("owner", owner.keyId())
                .query(Integer.class)
                .single();
    }
}
