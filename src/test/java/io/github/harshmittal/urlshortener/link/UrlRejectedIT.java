package io.github.harshmittal.urlshortener.link;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.github.harshmittal.urlshortener.support.ApiCalls;
import io.github.harshmittal.urlshortener.support.ApiCalls.Owner;
import io.github.harshmittal.urlshortener.support.AuditRows;
import io.github.harshmittal.urlshortener.support.IntegrationTest;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
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

/** HTTP shape and audit of URL policy rejections (AC13 to AC16) and the stored punycode form (AC17). */
@IntegrationTest
@AutoConfigureMockMvc
class UrlRejectedIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient jdbc;

    private Owner owner;

    @BeforeEach
    void newOwner() throws Exception {
        owner = ApiCalls.newOwner(mvc);
    }

    /** Targets are JSON string content, so {@code \\u0000} and {@code \\r\\n} arrive as real control characters. */
    static Stream<Arguments> rejectedTargets() {
        return Stream.of(
                Arguments.of("AC13, S-02", "http://2130706433/", "HOST_NOT_ALLOWED"),
                Arguments.of("AC13, S-02", "http://169.254.169.254/latest/meta-data/", "HOST_NOT_ALLOWED"),
                Arguments.of("AC14, S-03", "https://bank.com@evil.com/", "USERINFO_NOT_ALLOWED"),
                Arguments.of("AC15, S-04", "https://sho.rt/abc1234", "SELF_REFERENCE"),
                Arguments.of("AC16, S-05", "https://example.com/\\u0000", "MALFORMED"),
                Arguments.of("AC16, S-05", "https://example.com/\\r\\nSet-Cookie: x=y", "MALFORMED"),
                Arguments.of("AC16, S-05", "https://example.com/" + "a".repeat(2029), "TOO_LONG"));
    }

    @ParameterizedTest(name = "[{index}] {0}: {2}")
    @MethodSource("rejectedTargets")
    @DisplayName(
            "AC13-AC16: a policy rejection gets 400 url-rejected with its reason, a URL_REJECTED audit row, and no link")
    void rejectsWithReasonAndAudit(String criterion, String target, String reason) throws Exception {
        String requestId = "url-rejected-" + UUID.randomUUID();

        ApiCalls.createLink(mvc, owner.key(), target, requestId)
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("url-rejected"))
                .andExpect(jsonPath("$.reason").value(reason))
                .andExpect(jsonPath("$.requestId").value(requestId))
                .andExpect(jsonPath("$.detail").doesNotExist());

        assertThat(AuditRows.forRequest(jdbc, requestId)).singleElement().satisfies(event -> {
            assertThat(event.get("action")).isEqualTo("URL_REJECTED");
            assertThat(event.get("outcome")).isEqualTo("REJECTED");
            assertThat(event.get("reason_code")).isEqualTo(reason);
            assertThat(event.get("actor_key_id")).isEqualTo(owner.keyId());
            assertThat(event.get("resource_id")).isNull();
        });
        int stored = jdbc.sql("SELECT count(*) FROM link.links WHERE owner_key_id = :owner")
                .param("owner", owner.keyId())
                .query(Integer.class)
                .single();
        assertThat(stored).isZero();
    }

    @Test
    @DisplayName("AC17, S-05: an internationalized host is stored, returned and redirected to as punycode")
    void storesPunycodeHost() throws Exception {
        String body = ApiCalls.createLink(mvc, owner.key(), "https://bücher.example/ac17", "ac17-" + UUID.randomUUID())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.targetUrl").value("https://xn--bcher-kva.example/ac17"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String code = JsonPath.read(body, "$.code");
        String stored = jdbc.sql("SELECT target_url FROM link.links WHERE code = :code")
                .param("code", code)
                .query(String.class)
                .single();
        assertThat(stored).isEqualTo("https://xn--bcher-kva.example/ac17");
        assertThat(mvc.perform(get("/" + code)).andReturn().getResponse().getHeader("Location"))
                .isEqualTo("https://xn--bcher-kva.example/ac17");
    }
}
