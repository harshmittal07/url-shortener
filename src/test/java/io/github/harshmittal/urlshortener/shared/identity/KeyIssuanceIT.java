package io.github.harshmittal.urlshortener.shared.identity;

import static io.github.harshmittal.urlshortener.support.ApiCalls.bearer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.github.harshmittal.urlshortener.support.ApiCalls;
import io.github.harshmittal.urlshortener.support.AuditRows;
import io.github.harshmittal.urlshortener.support.IntegrationTest;
import io.github.harshmittal.urlshortener.support.Sha256;
import io.github.harshmittal.urlshortener.support.TestAdminKey;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class KeyIssuanceIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient jdbc;

    @Test
    @DisplayName("AC1: the admin key from new-admin-key.sh issues an owner key; only its hash and prefix are stored")
    void adminIssuesOwnerKey() throws Exception {
        String requestId = "ac1-" + UUID.randomUUID();

        String body = mvc.perform(post("/api/keys")
                        .header("Authorization", bearer(TestAdminKey.key()))
                        .header("X-Request-Id", requestId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isString())
                .andExpect(jsonPath("$.key").isString())
                .andExpect(jsonPath("$.createdAt").isString())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String id = JsonPath.read(body, "$.id");
        String key = JsonPath.read(body, "$.key");
        Map<String, Object> row = jdbc.sql("SELECT * FROM identity.api_keys WHERE id = :id")
                .param("id", UUID.fromString(id))
                .query()
                .singleRow();
        assertThat(row.get("key_prefix")).isEqualTo(ApiCalls.prefixOf(key));
        assertThat(row.get("key_hash")).isEqualTo(Sha256.hex(key));
        assertThat(row.values())
                .noneMatch(value -> value != null && value.toString().contains(secretOf(key)));

        assertThat(AuditRows.forRequest(jdbc, requestId)).singleElement().satisfies(event -> {
            assertThat(event.get("action")).isEqualTo("API_KEY_CREATED");
            assertThat(event.get("outcome")).isEqualTo("SUCCESS");
            assertThat(event.get("resource_id")).isEqualTo(id);
            assertThat(event.get("actor_key_id")).isEqualTo(new UUID(0L, 0L));
        });
    }

    @Test
    @DisplayName("AC2, S-12: after creation the key appears in no later response and in no log output")
    void keyIsShownOnlyOnce(CapturedOutput output) throws Exception {
        String key = ApiCalls.newOwnerKey(mvc);

        String later = mvc.perform(get("/api/links/abcdefg").header("Authorization", bearer(key)))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String anotherIssue = mvc.perform(post("/api/keys").header("Authorization", bearer(TestAdminKey.key())))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(later).doesNotContain(key).doesNotContain(secretOf(key));
        assertThat(anotherIssue).doesNotContain(key);
        assertThat(output.getAll()).doesNotContain(key).doesNotContain(secretOf(key));
        assertThat(output.getAll()).doesNotContain(TestAdminKey.key());
    }

    private static String secretOf(String key) {
        return key.substring("usk_".length() + 12 + 1);
    }
}
