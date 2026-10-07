package io.github.harshmittal.urlshortener.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectContainerResponse;
import io.github.harshmittal.urlshortener.support.IntegrationTest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.startupcheck.StartupCheckStrategy;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

/**
 * R34, AC48, D6: the generated OpenAPI document must not break the committed {@code
 * api/openapi.yaml}. oasdiff runs in a pinned container, so no local binary is needed (plan §11).
 */
@IntegrationTest
@AutoConfigureMockMvc
class ApiContractIT {

    private static final DockerImageName OASDIFF = DockerImageName.parse("tufin/oasdiff:v1.33.0");
    private static final Path COMMITTED = Path.of("api/openapi.yaml");

    @Autowired
    MockMvc mvc;

    @TempDir
    Path work;

    @Test
    @DisplayName("AC48: the generated contract has no breaking change against the committed api/openapi.yaml")
    void generatedContractIsCompatibleWithCommitted() throws Exception {
        Path revision = write("generated.yaml", generatedContract());

        OasdiffResult result = breaking(COMMITTED, revision);

        assertThat(result.exitCode()).as(result.output()).isZero();
    }

    @Test
    @DisplayName("AC48: a contract that removes a response field fails the check")
    void removedResponseFieldFailsTheCheck() throws Exception {
        Path revision = write("removed-field.yaml", withoutProperty(generatedContract(), "CreatedLink", "shortUrl"));

        OasdiffResult result = breaking(COMMITTED, revision);

        assertThat(result.exitCode()).as(result.output()).isNotZero();
        assertThat(result.output()).contains("shortUrl");
    }

    private String generatedContract() throws Exception {
        return mvc.perform(get("/v3/api-docs.yaml"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
    }

    /** The breaking-change fixture: the generated document with one schema property removed. */
    @SuppressWarnings("unchecked")
    private static String withoutProperty(String contract, String schema, String property) {
        Yaml yaml = new Yaml();
        Map<String, Object> document = yaml.load(contract);
        var schemas = (Map<String, Object>) ((Map<String, Object>) document.get("components")).get("schemas");
        var target = (Map<String, Object>) schemas.get(schema);
        assertThat((Map<String, Object>) target.get("properties"))
                .as("fixture precondition: %s.%s exists", schema, property)
                .containsKey(property);
        ((Map<String, Object>) target.get("properties")).remove(property);
        if (target.get("required") instanceof List<?> required) {
            required.remove(property);
        }
        var options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        return new Yaml(options).dump(document);
    }

    private Path write(String name, String content) throws Exception {
        return Files.writeString(work.resolve(name), content, StandardCharsets.UTF_8);
    }

    /** {@code oasdiff breaking base revision --fail-on ERR}: non-zero exit on any breaking change. */
    private static OasdiffResult breaking(Path base, Path revision) {
        try (var oasdiff = new GenericContainer<>(OASDIFF)
                .withCopyFileToContainer(MountableFile.forHostPath(base), "/contracts/base.yaml")
                .withCopyFileToContainer(MountableFile.forHostPath(revision), "/contracts/revision.yaml")
                .withCommand("breaking", "/contracts/base.yaml", "/contracts/revision.yaml", "--fail-on", "ERR")
                .withStartupCheckStrategy(new RunToCompletion())) {
            oasdiff.start();
            long exitCode = oasdiff.getCurrentContainerInfo().getState().getExitCodeLong();
            String output = oasdiff.getLogs();
            // Kept in the test report, so a failing build shows what broke.
            System.out.printf(
                    "oasdiff breaking %s -> %s: exit %d%n%s%n", base, revision.getFileName(), exitCode, output);
            return new OasdiffResult(exitCode, output);
        }
    }

    /** Waits for the one-shot container to exit, whatever its exit code; the test reads the code. */
    private static final class RunToCompletion extends StartupCheckStrategy {
        @Override
        public StartupStatus checkStartupState(DockerClient docker, String containerId) {
            InspectContainerResponse.ContainerState state = getCurrentState(docker, containerId);
            return Boolean.TRUE.equals(state.getRunning()) ? StartupStatus.NOT_YET_KNOWN : StartupStatus.SUCCESSFUL;
        }
    }

    private record OasdiffResult(long exitCode, String output) {}
}
