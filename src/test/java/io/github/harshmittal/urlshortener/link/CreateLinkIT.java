package io.github.harshmittal.urlshortener.link;

import static io.github.harshmittal.urlshortener.support.ApiCalls.bearer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.github.harshmittal.urlshortener.support.ApiCalls;
import io.github.harshmittal.urlshortener.support.ApiCalls.Owner;
import io.github.harshmittal.urlshortener.support.AuditRows;
import io.github.harshmittal.urlshortener.support.IntegrationTest;
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
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
@AutoConfigureMockMvc
class CreateLinkIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient jdbc;

    private Owner owner;

    @BeforeEach
    void newOwner() throws Exception {
        owner = ApiCalls.newOwner(mvc);
    }

    @Test
    @DisplayName("AC8: creating a link returns 201 with code, shortUrl, normalized targetUrl, and audits LINK_CREATED")
    void createsLink() throws Exception {
        String requestId = "ac8-" + UUID.randomUUID();

        String body = ApiCalls.createLink(mvc, owner.key(), "HTTPS://example.com/ac8?x=1#frag", requestId)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(org.hamcrest.Matchers.matchesPattern("[0-9A-Za-z]{7}")))
                .andExpect(jsonPath("$.targetUrl").value("https://example.com/ac8?x=1#frag"))
                .andExpect(jsonPath("$.createdAt").isString())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String code = JsonPath.read(body, "$.code");
        assertThat((String) JsonPath.read(body, "$.shortUrl")).isEqualTo("https://sho.rt/" + code);
        String stored = jdbc.sql("SELECT target_url FROM link.links WHERE code = :code AND owner_key_id = :owner")
                .param("code", code)
                .param("owner", owner.keyId())
                .query(String.class)
                .single();
        assertThat(stored).isEqualTo("https://example.com/ac8?x=1#frag");
        assertThat(AuditRows.forRequest(jdbc, requestId)).singleElement().satisfies(event -> {
            assertThat(event.get("action")).isEqualTo("LINK_CREATED");
            assertThat(event.get("outcome")).isEqualTo("SUCCESS");
            assertThat(event.get("resource_id")).isEqualTo(code);
            assertThat(event.get("actor_key_id")).isEqualTo(owner.keyId());
        });
    }

    @Test
    @DisplayName("AC8: the response Location header points at the new link")
    void setsLocation() throws Exception {
        var response = ApiCalls.createLink(mvc, owner.key(), "https://example.com/loc", "loc-" + UUID.randomUUID())
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse();

        String code = JsonPath.read(response.getContentAsString(), "$.code");
        assertThat(response.getHeader("Location")).isEqualTo("/api/links/" + code);
    }

    @Test
    @DisplayName("AC9, R6: shortening the same target twice returns two different codes")
    void sameTargetTwiceGivesTwoCodes() throws Exception {
        String first = ApiCalls.newLinkCode(mvc, owner.key(), "https://example.com/same");
        String second = ApiCalls.newLinkCode(mvc, owner.key(), "https://example.com/same");

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    @DisplayName("AC12, S-01: a javascript: target gets 400 url-rejected with SCHEME_NOT_ALLOWED and URL_REJECTED")
    void rejectsJavascriptScheme() throws Exception {
        String requestId = "ac12-" + UUID.randomUUID();

        ApiCalls.createLink(mvc, owner.key(), "javascript:alert(1)", requestId)
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("url-rejected"))
                .andExpect(jsonPath("$.reason").value("SCHEME_NOT_ALLOWED"))
                .andExpect(jsonPath("$.requestId").value(requestId));

        assertThat(AuditRows.forRequest(jdbc, requestId)).singleElement().satisfies(event -> {
            assertThat(event.get("action")).isEqualTo("URL_REJECTED");
            assertThat(event.get("reason_code")).isEqualTo("SCHEME_NOT_ALLOWED");
            assertThat(event.get("actor_key_id")).isEqualTo(owner.keyId());
        });
        int stored = jdbc.sql("SELECT count(*) FROM link.links WHERE target_url LIKE 'javascript:%'")
                .query(Integer.class)
                .single();
        assertThat(stored).isZero();
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {"", "{}", "{\"targetUrl\":null}", "{not json", "[]"})
    @DisplayName("R28: a missing or malformed body gets 400 validation-failed")
    void rejectsInvalidBody(String body) throws Exception {
        mvc.perform(post("/api/links")
                        .header("Authorization", bearer(owner.key()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("validation-failed"))
                .andExpect(jsonPath("$.detail").doesNotExist());
    }

    @Test
    @DisplayName("AGENTS.md §7: unknown request fields are ignored")
    void ignoresUnknownFields() throws Exception {
        mvc.perform(post("/api/links")
                        .header("Authorization", bearer(owner.key()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetUrl\":\"https://example.com/u\",\"label\":\"ignored\"}"))
                .andExpect(status().isCreated());
    }
}
