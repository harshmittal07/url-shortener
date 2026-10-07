package io.github.harshmittal.urlshortener.shared.web;

import static io.github.harshmittal.urlshortener.support.ApiCalls.bearer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.github.harshmittal.urlshortener.support.FakeKeys;
import io.github.harshmittal.urlshortener.support.IntegrationTest;
import io.github.harshmittal.urlshortener.support.TestAdminKey;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * S-12: the AC1, AC3, AC8, AC23 and AC26 flows log no API key, full target URL, raw client IP or
 * request body. Same limit as {@code CreationRateLimitIT}, so the two share one application context.
 */
@IntegrationTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
@TestPropertySource(properties = "url-shortener.link-create-limit-per-minute=3")
class LogHygieneIT {

    /** A documentation-range address (RFC 5737) that nothing else in the test output contains. */
    private static final String CLIENT_IP = "198.51.100.23";

    @Autowired
    MockMvc mvc;

    @Test
    @DisplayName(
            "AC40, S-12: key issuance, auth failure, creation, redirect and rate limit log no secret, URL, IP or body")
    void flowsLogNothingSensitive(CapturedOutput output) throws Exception {
        String unique = UUID.randomUUID().toString();
        String target = "https://example.com/private/" + unique + "?token=secret-" + unique;
        String body = "{\"targetUrl\":\"" + target + "\"}";

        // AC1: the admin issues an owner key.
        String issued = mvc.perform(fromClient(post("/api/keys").header("Authorization", bearer(TestAdminKey.key()))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String ownerKey = JsonPath.read(issued, "$.key");

        // AC3: unknown and malformed keys are rejected.
        String unknownKey = FakeKeys.withPrefix("loghygiene00", 'z');
        String malformedKey = "usk_malformed-" + unique;
        mvc.perform(fromClient(get("/api/links/abcdefg").header("Authorization", bearer(unknownKey))))
                .andExpect(status().isUnauthorized());
        mvc.perform(fromClient(get("/api/links/abcdefg").header("Authorization", bearer(malformedKey))))
                .andExpect(status().isUnauthorized());

        // AC8: the owner creates a link.
        String created = mvc.perform(fromClient(post("/api/links")
                        .header("Authorization", bearer(ownerKey))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String code = JsonPath.read(created, "$.code");

        // AC23: a visitor follows it.
        mvc.perform(fromClient(get("/" + code))).andExpect(status().isFound());

        // AC26: the owner goes over the creation limit (3 here; one used above).
        for (int attempt = 2; attempt <= 3; attempt++) {
            mvc.perform(fromClient(post("/api/links")
                            .header("Authorization", bearer(ownerKey))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body)))
                    .andExpect(status().isCreated());
        }
        mvc.perform(fromClient(post("/api/links")
                        .header("Authorization", bearer(ownerKey))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)))
                .andExpect(status().isTooManyRequests());

        String logs = output.getAll();
        assertThat(logs).as("the flows were logged at all").contains("Link created: code=" + code);
        assertThat(logs)
                .doesNotContain(ownerKey)
                .doesNotContain(secretOf(ownerKey))
                .doesNotContain(TestAdminKey.key())
                .doesNotContain(unknownKey)
                .doesNotContain(malformedKey)
                .doesNotContain(target)
                .doesNotContain(unique)
                .doesNotContain(body)
                .doesNotContain(CLIENT_IP);
    }

    private static MockHttpServletRequestBuilder fromClient(MockHttpServletRequestBuilder request) {
        return request.with(servletRequest -> {
            servletRequest.setRemoteAddr(CLIENT_IP);
            return servletRequest;
        });
    }

    private static String secretOf(String key) {
        return key.substring("usk_".length() + 12 + 1);
    }
}
