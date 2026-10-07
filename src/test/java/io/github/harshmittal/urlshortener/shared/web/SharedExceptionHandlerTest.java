package io.github.harshmittal.urlshortener.shared.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

class SharedExceptionHandlerTest {

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new FailingController())
            .setControllerAdvice(new SharedExceptionHandler())
            .addFilters(new RequestIdFilter(UUID::randomUUID))
            .build();

    @Test
    @DisplayName("S-16: an unexpected exception becomes 500 internal-error problem+json with the request ID")
    void unexpectedExceptionIsInternalError() throws Exception {
        MvcResult result = mvc.perform(get("/boom").header("X-Request-Id", "req-500"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:url-shortener:problem:internal-error"))
                .andExpect(jsonPath("$.title").value("Internal Server Error"))
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.code").value("internal-error"))
                .andExpect(jsonPath("$.requestId").value("req-500"))
                .andReturn();

        assertThat(result.getResponse().getContentAsString())
                .doesNotContain(FailingController.SECRET_DETAIL)
                .doesNotContain("IllegalStateException")
                .doesNotContain("java.")
                .doesNotContain("\tat ");
    }

    @Test
    @DisplayName("S-16: the requestId property equals the X-Request-Id response header")
    void requestIdMatchesHeader() throws Exception {
        MvcResult result = mvc.perform(get("/boom")).andReturn();

        String header = result.getResponse().getHeader("X-Request-Id");
        assertThat(header).isNotBlank();
        assertThat(result.getResponse().getContentAsString()).contains("\"requestId\":\"" + header + "\"");
    }

    @Test
    @DisplayName("R13, R28: an unknown path is 404 not-found problem+json")
    void unknownPathIsNotFound() throws Exception {
        mvc.perform(get("/no/such/path"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("not-found"))
                .andExpect(jsonPath("$.type").value("urn:url-shortener:problem:not-found"));
    }

    @Test
    @DisplayName("R28: framework errors without a spec code keep their status and get a stable code, no detail")
    void frameworkErrorKeepsStatusWithStableCode() throws Exception {
        mvc.perform(post("/boom"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("method-not-allowed"))
                .andExpect(jsonPath("$.detail").doesNotExist());
    }

    @RestController
    static class FailingController {
        static final String SECRET_DETAIL = "SELECT secret FROM internals";

        @GetMapping("/boom")
        String boom() {
            throw new IllegalStateException(SECRET_DETAIL);
        }
    }
}
