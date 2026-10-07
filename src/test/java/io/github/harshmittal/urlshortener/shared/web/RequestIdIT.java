package io.github.harshmittal.urlshortener.shared.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import io.github.harshmittal.urlshortener.support.IntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/** Runs through the full filter chain, including Spring Security, to check the filter order. */
@IntegrationTest
@AutoConfigureMockMvc
class RequestIdIT {

    @Autowired
    MockMvc mvc;

    @Test
    @DisplayName("AC41: a valid X-Request-Id is echoed, even on a rejected request")
    void echoesValidRequestId() throws Exception {
        String header = mvc.perform(get("/api/links/abcdefg").header("X-Request-Id", "client-req.42"))
                .andReturn()
                .getResponse()
                .getHeader("X-Request-Id");

        assertThat(header).isEqualTo("client-req.42");
    }

    @Test
    @DisplayName("AC41, A5: an invalid X-Request-Id is replaced with a generated UUID")
    void replacesInvalidRequestId() throws Exception {
        String header = mvc.perform(get("/api/links/abcdefg").header("X-Request-Id", "x".repeat(65)))
                .andReturn()
                .getResponse()
                .getHeader("X-Request-Id");

        assertThat(header).isNotNull();
        assertThat(UUID.fromString(header)).isNotNull();
    }

    @Test
    @DisplayName("AC41: a missing X-Request-Id gets a generated UUID")
    void generatesMissingRequestId() throws Exception {
        String header = mvc.perform(get("/abcdefg")).andReturn().getResponse().getHeader("X-Request-Id");

        assertThat(header).isNotNull();
        assertThat(UUID.fromString(header)).isNotNull();
    }
}
