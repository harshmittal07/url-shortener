package io.github.harshmittal.urlshortener.app;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.harshmittal.urlshortener.support.ServerIntegrationTest;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.Properties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ClassPathResource;

/**
 * R31, S-17: the actuator lives on its own port and exposes only health and info. The test runs both
 * ports on random numbers; the shipped port is checked from {@code application.yaml}.
 */
@ServerIntegrationTest
class ActuatorExposureIT {

    private final HttpClient http = HttpClient.newHttpClient();

    @LocalServerPort
    int publicPort;

    @LocalManagementPort
    int managementPort;

    @Test
    @DisplayName("AC45, S-17: the shipped configuration puts the actuator on port 8081, apart from the API")
    void shippedConfigurationUsesSeparatePort() {
        var yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yaml"));
        Properties shipped = yaml.getObject();

        assertThat(shipped).containsEntry("management.server.port", 8081);
        assertThat(shipped.get("server.port")).isNotEqualTo(8081);
        assertThat(managementPort).isNotEqualTo(publicPort);
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {"/actuator", "/actuator/health", "/actuator/info", "/actuator/env"})
    @DisplayName("AC45, S-17: the public port serves nothing under /actuator")
    void publicPortServesNoActuator(String path) throws Exception {
        HttpResponse<String> response = get(publicPort, path);

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body()).doesNotContain("\"status\":\"UP\"").doesNotContain("_links");
    }

    @Test
    @DisplayName("AC45, S-17: the management port serves health without details")
    void managementPortServesHealth() throws Exception {
        HttpResponse<String> response = get(managementPort, "/actuator/health");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"status\":\"UP\"").doesNotContain("components");
    }

    @Test
    @DisplayName("AC45, S-17: the management port serves info")
    void managementPortServesInfo() throws Exception {
        assertThat(get(managementPort, "/actuator/info").statusCode()).isEqualTo(200);
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(
            strings = {
                "env",
                "beans",
                "configprops",
                "heapdump",
                "threaddump",
                "loggers",
                "mappings",
                "metrics",
                "conditions",
                "scheduledtasks",
                "shutdown",
                "prometheus"
            })
    @DisplayName("AC45, S-17: the management port exposes no endpoint other than health and info")
    void managementPortExposesNothingElse(String endpoint) throws Exception {
        assertThat(get(managementPort, "/actuator/" + endpoint).statusCode()).isEqualTo(404);
    }

    private HttpResponse<String> get(int port, String path) throws Exception {
        return http.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                        .build(),
                BodyHandlers.ofString());
    }
}
