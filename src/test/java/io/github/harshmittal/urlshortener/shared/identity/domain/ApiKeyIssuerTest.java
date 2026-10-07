package io.github.harshmittal.urlshortener.shared.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.harshmittal.urlshortener.shared.audit.domain.AuditAction;
import io.github.harshmittal.urlshortener.shared.audit.domain.AuditContext;
import io.github.harshmittal.urlshortener.shared.audit.domain.AuditTrail;
import io.github.harshmittal.urlshortener.shared.audit.domain.InMemoryAuditSink;
import io.github.harshmittal.urlshortener.shared.audit.domain.Outcome;
import io.github.harshmittal.urlshortener.shared.tx.domain.InlineUnitOfWork;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ApiKeyIssuerTest {

    private static final UUID KEY_ID = UUID.fromString("00000000-0000-4000-8000-0000000000b1");
    private static final KeyMaterial MATERIAL = new KeyMaterial("Prefix123456", "s".repeat(43));
    private static final Instant NOW = Instant.parse("2026-10-07T10:00:00Z");
    private static final AuditContext ADMIN_CONTEXT =
            new AuditContext("req-key", Principal.ADMIN_KEY_ID, "c".repeat(64));

    private final InMemoryApiKeyRepository keys = new InMemoryApiKeyRepository();
    private final InMemoryAuditSink auditSink = new InMemoryAuditSink();
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final ApiKeyIssuer issuer = new ApiKeyIssuer(
            keys,
            () -> MATERIAL,
            new AuditTrail(auditSink, new InlineUnitOfWork(), clock, UUID::randomUUID),
            new InlineUnitOfWork(),
            clock,
            () -> KEY_ID);

    @Test
    @DisplayName("AC1, R2: the issued key is returned once, in the usk_<prefix>_<secret> format")
    void returnsKey() {
        IssuedApiKey issued = issuer.issue(ADMIN_CONTEXT);

        assertThat(issued.id()).isEqualTo(KEY_ID);
        assertThat(issued.key()).isEqualTo("usk_Prefix123456_" + "s".repeat(43));
        assertThat(issued.createdAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("AC1, S-07: only the prefix and the SHA-256 hash of the key are stored")
    void storesOnlyPrefixAndHash() {
        IssuedApiKey issued = issuer.issue(ADMIN_CONTEXT);

        assertThat(keys.all()).singleElement().satisfies(stored -> {
            assertThat(stored.prefix()).isEqualTo("Prefix123456");
            assertThat(stored.keyHash()).isEqualTo(ApiKeyToken.sha256Hex(issued.key()));
            assertThat(stored.toString()).doesNotContain(MATERIAL.secret());
        });
    }

    @Test
    @DisplayName("AC1: an API_KEY_CREATED event with outcome SUCCESS names the new key")
    void auditsCreation() {
        issuer.issue(ADMIN_CONTEXT);

        assertThat(auditSink.events()).singleElement().satisfies(event -> {
            assertThat(event.action()).isEqualTo(AuditAction.API_KEY_CREATED);
            assertThat(event.outcome()).isEqualTo(Outcome.SUCCESS);
            assertThat(event.actorKeyId()).isEqualTo(Principal.ADMIN_KEY_ID);
            assertThat(event.resourceId()).isEqualTo(KEY_ID.toString());
        });
    }

    @Test
    @DisplayName("S-12: an issued key never prints the key")
    void issuedKeyHidesKey() {
        IssuedApiKey issued = issuer.issue(ADMIN_CONTEXT);

        assertThat(issued.toString()).doesNotContain(issued.key());
    }
}
