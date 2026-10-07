package io.github.harshmittal.urlshortener.app;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.harshmittal.urlshortener.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** R32, A11: the OpenAPI document is public; Swagger UI exists only in the {@code local} profile. */
class OpenApiExposureIT {

    @Nested
    @IntegrationTest
    @AutoConfigureMockMvc
    class DefaultProfile {

        @Autowired
        MockMvc mvc;

        @Test
        @DisplayName("AC46: the OpenAPI document is served without a key")
        void servesOpenApiDocument() throws Exception {
            mvc.perform(get("/v3/api-docs"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.openapi").exists())
                    .andExpect(jsonPath("$.paths['/api/links']").exists());
        }

        @ParameterizedTest(name = "[{index}] {0}")
        @ValueSource(strings = {"/swagger-ui.html", "/swagger-ui/index.html", "/v3/api-docs/swagger-config"})
        @DisplayName("AC46: Swagger UI returns 404 by default")
        void swaggerUiIsOff(String path) throws Exception {
            mvc.perform(get(path)).andExpect(status().isNotFound());
        }
    }

    @Nested
    @IntegrationTest
    @AutoConfigureMockMvc
    @ActiveProfiles({"integration", "local"})
    class LocalProfile {

        @Autowired
        MockMvc mvc;

        @Test
        @DisplayName("AC46: the local profile serves Swagger UI")
        void swaggerUiIsOn() throws Exception {
            mvc.perform(get("/swagger-ui/index.html"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));
        }
    }
}
