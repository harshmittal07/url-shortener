package io.github.harshmittal.urlshortener.shared.web;

import static io.github.harshmittal.urlshortener.support.ApiCalls.bearer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.harshmittal.urlshortener.support.IntegrationTest;
import io.github.harshmittal.urlshortener.support.TestAdminKey;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** R24, S-12: log lines written while serving a request are ECS JSON and carry the request ID. */
@IntegrationTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class EcsLogFormatIT {

    private final JsonMapper json = JsonMapper.builder().build();

    @Autowired
    MockMvc mvc;

    @Test
    @DisplayName("AC38: every line logged while serving requests is ECS JSON with the request's requestId")
    void requestLogLinesAreEcsJsonWithRequestId(CapturedOutput output) throws Exception {
        String issueId = "ecs-issue-" + UUID.randomUUID();
        String rejectId = "ecs-reject-" + UUID.randomUUID();

        // INFO line: key created.
        mvc.perform(post("/api/keys")
                        .header("X-Request-Id", issueId)
                        .header("Authorization", bearer(TestAdminKey.key())))
                .andExpect(status().isCreated());
        // WARN line: authentication failed.
        mvc.perform(post("/api/keys").header("X-Request-Id", rejectId)).andExpect(status().isUnauthorized());

        List<String> lines =
                output.getOut().lines().filter(line -> !line.isBlank()).toList();
        assertThat(lines).as("the requests were logged").isNotEmpty();

        List<JsonNode> events = lines.stream().map(this::parse).toList();
        for (JsonNode event : events) {
            assertThat(event.path("@timestamp").isString())
                    .as("@timestamp in %s", event)
                    .isTrue();
            assertThat(event.path("log").path("level").isString())
                    .as("log.level in %s", event)
                    .isTrue();
            assertThat(event.path("log").path("logger").isString())
                    .as("log.logger in %s", event)
                    .isTrue();
            assertThat(event.path("message").isString())
                    .as("message in %s", event)
                    .isTrue();
            assertThat(event.path("ecs").path("version").isString())
                    .as("ecs.version in %s", event)
                    .isTrue();
        }
        assertThat(events)
                .filteredOn(event -> event.path("message").asString().startsWith("API key created"))
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.path("log").path("level").asString()).isEqualTo("INFO");
                    assertThat(event.path("requestId").asString()).isEqualTo(issueId);
                });
        assertThat(events)
                .filteredOn(event -> event.path("message").asString().startsWith("Authentication failed"))
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.path("log").path("level").asString()).isEqualTo("WARN");
                    assertThat(event.path("requestId").asString()).isEqualTo(rejectId);
                });
    }

    private JsonNode parse(String line) {
        try {
            return json.readTree(line);
        } catch (RuntimeException e) {
            throw new AssertionError("Not a JSON log line: " + line, e);
        }
    }
}
