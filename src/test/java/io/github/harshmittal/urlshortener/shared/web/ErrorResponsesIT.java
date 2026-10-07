package io.github.harshmittal.urlshortener.shared.web;

import static io.github.harshmittal.urlshortener.support.ApiCalls.bearer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.harshmittal.urlshortener.link.api.LinkLookup;
import io.github.harshmittal.urlshortener.support.ApiCalls;
import io.github.harshmittal.urlshortener.support.AuditRows;
import io.github.harshmittal.urlshortener.support.IntegrationTest;
import io.github.harshmittal.urlshortener.support.RequestBodies;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

/**
 * AC42 through the full stack: an unexpected exception and a {@code 413}. The database-outage
 * {@code 503} is checked by {@code scripts/smoke-test.sh} (D15); its mapping is unit-tested in
 * {@code SharedExceptionHandlerTest} and {@code ApiKeyAuthenticationFilterTest}.
 */
@IntegrationTest
@AutoConfigureMockMvc
@Import(ErrorResponsesIT.FailingLookup.class)
class ErrorResponsesIT {

    static final String INTERNAL_DETAIL = "SELECT target_url FROM link.links WHERE code = 'internal-detail'";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient jdbc;

    @Test
    @DisplayName("AC42, S-16: an unexpected exception is 500 internal-error problem+json with no internal detail")
    void unexpectedExceptionIsSafe() throws Exception {
        MockHttpServletResponse response = mvc.perform(get("/abcdefg"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("internal-error"))
                .andReturn()
                .getResponse();

        assertRequestIdMatchesHeader(response);
        assertNoInternals(response.getContentAsString());
    }

    @Test
    @DisplayName("AC42, AC44: an oversized body is 413 payload-too-large problem+json with no internal detail")
    void oversizedBodyIsSafe() throws Exception {
        String ownerKey = ApiCalls.newOwnerKey(mvc);

        MockHttpServletResponse response = mvc.perform(post("/api/links")
                        .header("Authorization", bearer(ownerKey))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(RequestBodies.createLinkOfSize("https://example.com/ac42", 8193)))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("payload-too-large"))
                .andExpect(jsonPath("$.type").value("urn:url-shortener:problem:payload-too-large"))
                .andExpect(jsonPath("$.detail").doesNotExist())
                .andReturn()
                .getResponse();

        assertRequestIdMatchesHeader(response);
        assertNoInternals(response.getContentAsString());
    }

    @Test
    @DisplayName("R30, R15: an oversized body is rejected with 413 before authentication, so nothing is audited")
    void oversizedBodyIsRejectedBeforeAuthentication() throws Exception {
        String requestId = "r30-" + UUID.randomUUID();

        mvc.perform(post("/api/links")
                        .header("X-Request-Id", requestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(RequestBodies.createLinkOfSize("https://example.com/r30", 8193)))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("payload-too-large"))
                .andExpect(jsonPath("$.requestId").value(requestId));

        assertThat(AuditRows.forRequest(jdbc, requestId)).isEmpty();
    }

    private static void assertRequestIdMatchesHeader(MockHttpServletResponse response) throws Exception {
        String header = response.getHeader("X-Request-Id");
        assertThat(header).isNotBlank();
        assertThat(response.getContentAsString()).contains("\"requestId\":\"" + header + "\"");
    }

    private static void assertNoInternals(String body) {
        assertThat(body)
                .doesNotContain(INTERNAL_DETAIL)
                .doesNotContain("SELECT")
                .doesNotContain("Exception")
                .doesNotContain("io.github")
                .doesNotContain("java.")
                .doesNotContain("\tat ");
    }

    /** Makes every redirect lookup fail unexpectedly, with an internal-looking message. */
    @TestConfiguration(proxyBeanMethods = false)
    static class FailingLookup {
        @Bean
        @Primary
        LinkLookup failingLinkLookup() {
            return code -> {
                throw new IllegalStateException(INTERNAL_DETAIL);
            };
        }
    }
}
