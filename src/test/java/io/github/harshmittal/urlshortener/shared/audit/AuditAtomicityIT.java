package io.github.harshmittal.urlshortener.shared.audit;

import static io.github.harshmittal.urlshortener.support.ApiCalls.bearer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.harshmittal.urlshortener.shared.audit.adapter.out.persistence.JdbcAuditSink;
import io.github.harshmittal.urlshortener.shared.audit.domain.AuditAction;
import io.github.harshmittal.urlshortener.shared.audit.domain.AuditEvent;
import io.github.harshmittal.urlshortener.shared.audit.domain.AuditSink;
import io.github.harshmittal.urlshortener.support.ApiCalls;
import io.github.harshmittal.urlshortener.support.AuditRows;
import io.github.harshmittal.urlshortener.support.IntegrationTest;
import io.github.harshmittal.urlshortener.support.TestAdminKey;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
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

/** R18: a state change and its audit event commit together, or not at all. */
@IntegrationTest
@AutoConfigureMockMvc
@Import(AuditAtomicityIT.FailingStateChangeAudit.class)
class AuditAtomicityIT {

    static final AtomicBoolean FAIL_STATE_CHANGES = new AtomicBoolean();

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient jdbc;

    @AfterEach
    void restoreAudit() {
        FAIL_STATE_CHANGES.set(false);
    }

    @Test
    @DisplayName("AC30, S-10: if the LINK_CREATED write fails, the link is rolled back and the client gets 500")
    void linkCreationRollsBack() throws Exception {
        String ownerKey = ApiCalls.newOwnerKey(mvc);
        String requestId = "ac30-" + UUID.randomUUID();
        String target = "https://example.com/" + requestId;
        FAIL_STATE_CHANGES.set(true);

        ApiCalls.createLink(mvc, ownerKey, target, requestId)
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("internal-error"));

        int stored = jdbc.sql("SELECT count(*) FROM link.links WHERE target_url = :target")
                .param("target", target)
                .query(Integer.class)
                .single();
        assertThat(stored).isZero();
        assertThat(AuditRows.forRequest(jdbc, requestId)).isEmpty();
    }

    @Test
    @DisplayName("AC30, S-10: if the LINK_DELETED write fails, the delete is rolled back and the client gets 500")
    void linkDeletionRollsBack() throws Exception {
        String ownerKey = ApiCalls.newOwnerKey(mvc);
        String code = ApiCalls.newLinkCode(mvc, ownerKey, "https://example.com/ac30-delete");
        String requestId = "ac30-del-" + UUID.randomUUID();
        FAIL_STATE_CHANGES.set(true);

        mvc.perform(delete("/api/links/" + code)
                        .header("Authorization", bearer(ownerKey))
                        .header("X-Request-Id", requestId))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("internal-error"));

        String linkStatus = jdbc.sql("SELECT status FROM link.links WHERE code = :code")
                .param("code", code)
                .query(String.class)
                .single();
        assertThat(linkStatus).isEqualTo("ACTIVE");
        assertThat(AuditRows.forRequest(jdbc, requestId)).isEmpty();
    }

    @Test
    @DisplayName("R18, S-10: if the API_KEY_CREATED write fails, the key is rolled back and the client gets 500")
    void keyCreationRollsBack() throws Exception {
        long keysBefore = keyRows();
        FAIL_STATE_CHANGES.set(true);

        mvc.perform(post("/api/keys").header("Authorization", bearer(TestAdminKey.key())))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("internal-error"))
                .andExpect(jsonPath("$.key").doesNotExist());

        assertThat(keyRows()).isEqualTo(keysBefore);
    }

    private long keyRows() {
        return jdbc.sql("SELECT count(*) FROM identity.api_keys")
                .query(Long.class)
                .single();
    }

    /** Wraps the real sink and fails state-change events on demand; rejections still write. */
    @TestConfiguration(proxyBeanMethods = false)
    static class FailingStateChangeAudit {
        private static final Set<AuditAction> STATE_CHANGES =
                Set.of(AuditAction.API_KEY_CREATED, AuditAction.LINK_CREATED, AuditAction.LINK_DELETED);

        @Bean
        @Primary
        AuditSink failingStateChangeAuditSink(JdbcClient jdbc) {
            AuditSink real = new JdbcAuditSink(jdbc);
            return (AuditEvent event) -> {
                if (FAIL_STATE_CHANGES.get() && STATE_CHANGES.contains(event.action())) {
                    throw new IllegalStateException("audit store unavailable");
                }
                real.append(event);
            };
        }
    }
}
