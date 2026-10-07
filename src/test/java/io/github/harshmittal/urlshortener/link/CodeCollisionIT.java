package io.github.harshmittal.urlshortener.link;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.harshmittal.urlshortener.link.adapter.out.persistence.JdbcLinkRepository;
import io.github.harshmittal.urlshortener.link.domain.Link;
import io.github.harshmittal.urlshortener.link.domain.LinkStatus;
import io.github.harshmittal.urlshortener.link.domain.ScriptedShortCodeGenerator;
import io.github.harshmittal.urlshortener.link.domain.ShortCode;
import io.github.harshmittal.urlshortener.link.domain.ShortCodeGenerator;
import io.github.harshmittal.urlshortener.link.domain.TestCodes;
import io.github.harshmittal.urlshortener.support.ApiCalls;
import io.github.harshmittal.urlshortener.support.AuditRows;
import io.github.harshmittal.urlshortener.support.IntegrationTest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

/** Real database collisions: exercises ON CONFLICT DO NOTHING and the retry in one transaction. */
@IntegrationTest
@AutoConfigureMockMvc
@Import(CodeCollisionIT.ScriptedCodes.class)
class CodeCollisionIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    SwitchableCodes codes;

    private ShortCode taken;
    private String ownerKey;

    @BeforeEach
    void givenTakenCode() throws Exception {
        ownerKey = ApiCalls.newOwnerKey(mvc);
        taken = TestCodes.random();
        new JdbcLinkRepository(jdbc)
                .insertIfCodeFree(new Link(
                        UUID.randomUUID(),
                        taken,
                        "https://example.com/taken",
                        UUID.randomUUID(),
                        LinkStatus.ACTIVE,
                        Instant.parse("2026-10-07T00:00:00Z")));
    }

    @Test
    @DisplayName("AC10, R7: two collisions then a free code creates the link with the free code")
    void succeedsAfterTwoCollisions() throws Exception {
        ShortCode free = TestCodes.random();
        codes.use(new ScriptedShortCodeGenerator(List.of(taken, taken, free)));

        ApiCalls.createLink(mvc, ownerKey, "https://example.com/ac10", "ac10-" + UUID.randomUUID())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(free.value()));
    }

    @Test
    @DisplayName("AC11, R7: four collisions give 503 code-generation-failed and store nothing")
    void failsAfterFourCollisions() throws Exception {
        String requestId = "ac11-" + UUID.randomUUID();
        codes.use(new ScriptedShortCodeGenerator(List.of(taken, taken, taken, taken)));

        ApiCalls.createLink(mvc, ownerKey, "https://example.com/ac11-" + requestId, requestId)
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("code-generation-failed"));

        int stored = jdbc.sql("SELECT count(*) FROM link.links WHERE target_url = :target")
                .param("target", "https://example.com/ac11-" + requestId)
                .query(Integer.class)
                .single();
        assertThat(stored).isZero();
        assertThat(AuditRows.forRequest(jdbc, requestId)).isEmpty();
    }

    /** Lets each test script the codes the service draws. */
    static final class SwitchableCodes implements ShortCodeGenerator {
        private volatile ShortCodeGenerator delegate;

        void use(ShortCodeGenerator generator) {
            this.delegate = generator;
        }

        @Override
        public ShortCode next() {
            return delegate.next();
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ScriptedCodes {
        @Bean
        @Primary
        SwitchableCodes scriptedShortCodeGenerator() {
            return new SwitchableCodes();
        }
    }
}
