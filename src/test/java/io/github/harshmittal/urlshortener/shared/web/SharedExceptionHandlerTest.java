package io.github.harshmittal.urlshortener.shared.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.CannotCreateTransactionException;
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

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {"/db/connection", "/db/transaction", "/db/resource"})
    @DisplayName("AC42, S-16: a database outage becomes 503 service-unavailable with no internal detail")
    void databaseOutageIsServiceUnavailable(String path) throws Exception {
        MvcResult result = mvc.perform(get(path).header("X-Request-Id", "req-503"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("service-unavailable"))
                .andExpect(jsonPath("$.type").value("urn:url-shortener:problem:service-unavailable"))
                .andExpect(jsonPath("$.requestId").value("req-503"))
                .andExpect(jsonPath("$.detail").doesNotExist())
                .andReturn();

        assertThat(result.getResponse().getContentAsString())
                .doesNotContain(FailingController.DB_DETAIL)
                .doesNotContain("Exception")
                .doesNotContain("jdbc:");
    }

    @RestController
    static class FailingController {
        static final String SECRET_DETAIL = "SELECT secret FROM internals";
        static final String DB_DETAIL = "Failed to obtain JDBC Connection: jdbc:postgresql://db:5432/urlshortener";

        @GetMapping("/boom")
        String boom() {
            throw new IllegalStateException(SECRET_DETAIL);
        }

        /** What JdbcClient throws when Hikari cannot hand out a connection. */
        @GetMapping("/db/connection")
        String connection() {
            throw new CannotGetJdbcConnectionException(DB_DETAIL, new SQLException(DB_DETAIL));
        }

        /** What TransactionTemplate throws when it cannot open a connection to begin. */
        @GetMapping("/db/transaction")
        String transaction() {
            throw new CannotCreateTransactionException(DB_DETAIL, new SQLException(DB_DETAIL));
        }

        /** What a query on a broken pooled connection becomes (SQL state class 08). */
        @GetMapping("/db/resource")
        String resource() {
            throw new DataAccessResourceFailureException(DB_DETAIL);
        }
    }
}
