package io.github.harshmittal.urlshortener.link.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.harshmittal.urlshortener.shared.audit.domain.AuditAction;
import io.github.harshmittal.urlshortener.shared.audit.domain.AuditContext;
import io.github.harshmittal.urlshortener.shared.audit.domain.AuditTrail;
import io.github.harshmittal.urlshortener.shared.audit.domain.InMemoryAuditSink;
import io.github.harshmittal.urlshortener.shared.audit.domain.Outcome;
import io.github.harshmittal.urlshortener.shared.tx.domain.InlineUnitOfWork;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LinkServiceTest {

    private static final UUID OWNER = UUID.fromString("00000000-0000-4000-8000-0000000000c1");
    private static final UUID OTHER_OWNER = UUID.fromString("00000000-0000-4000-8000-0000000000c2");
    private static final AuditContext CONTEXT = new AuditContext("req-link", OWNER, "c".repeat(64));
    private static final Instant NOW = Instant.parse("2026-10-07T10:00:00Z");
    private static final ShortCode TAKEN = new ShortCode("TAKEN01");
    private static final ShortCode FREE = new ShortCode("FREE001");

    private final InMemoryLinkRepository links = new InMemoryLinkRepository();
    private final InMemoryAuditSink auditSink = new InMemoryAuditSink();
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    private LinkService service(ShortCodeGenerator codes) {
        return new LinkService(
                links,
                codes,
                new StandardUrlPolicy(),
                new AuditTrail(auditSink, new InlineUnitOfWork(), clock, UUID::randomUUID),
                new InlineUnitOfWork(),
                clock,
                UUID::randomUUID);
    }

    @Test
    @DisplayName("AC8: an allowed target is stored as an active link owned by the caller, with LINK_CREATED")
    void createsLink() {
        Link link =
                service(new ScriptedShortCodeGenerator(List.of(FREE))).create("https://example.com/x", OWNER, CONTEXT);

        assertThat(link.code()).isEqualTo(FREE);
        assertThat(link.ownerKeyId()).isEqualTo(OWNER);
        assertThat(link.status()).isEqualTo(LinkStatus.ACTIVE);
        assertThat(link.createdAt()).isEqualTo(NOW);
        assertThat(links.findByCode(FREE)).contains(link);
        assertThat(auditSink.events()).singleElement().satisfies(event -> {
            assertThat(event.action()).isEqualTo(AuditAction.LINK_CREATED);
            assertThat(event.outcome()).isEqualTo(Outcome.SUCCESS);
            assertThat(event.resourceId()).isEqualTo("FREE001");
        });
    }

    @Test
    @DisplayName("AC8: the stored target is the normalized form")
    void storesNormalizedTarget() {
        Link link = service(new ScriptedShortCodeGenerator(List.of(FREE)))
                .create("  HTTPS://example.com/x ", OWNER, CONTEXT);

        assertThat(link.targetUrl()).isEqualTo("https://example.com/x");
    }

    @Test
    @DisplayName("AC9, R6: shortening the same target twice gives two different codes")
    void doesNotDeduplicate() {
        var service = service(new ScriptedShortCodeGenerator(List.of(FREE, new ShortCode("FREE002"))));

        Link first = service.create("https://example.com/same", OWNER, CONTEXT);
        Link second = service.create("https://example.com/same", OWNER, CONTEXT);

        assertThat(first.code()).isNotEqualTo(second.code());
        assertThat(links.all()).hasSize(2);
    }

    @Test
    @DisplayName("AC10, R7: two collisions then a free code succeeds with the free code")
    void retriesAfterCollisions() {
        givenTaken(TAKEN);
        var generator = new ScriptedShortCodeGenerator(List.of(TAKEN, TAKEN, FREE));

        Link link = service(generator).create("https://example.com/x", OWNER, CONTEXT);

        assertThat(link.code()).isEqualTo(FREE);
        assertThat(generator.remaining()).isZero();
    }

    @Test
    @DisplayName("AC11, R7: four collisions (1 + 3 retries) fail, store nothing and write no LINK_CREATED")
    void failsAfterFourCollisions() {
        givenTaken(TAKEN);
        var generator = new ScriptedShortCodeGenerator(List.of(TAKEN, TAKEN, TAKEN, TAKEN, FREE));

        assertThatThrownBy(() -> service(generator).create("https://example.com/x", OWNER, CONTEXT))
                .isInstanceOf(CodeGenerationFailedException.class);

        assertThat(generator.remaining()).as("a fifth attempt was never made").isEqualTo(1);
        assertThat(links.all()).hasSize(1);
        assertThat(auditSink.events()).isEmpty();
    }

    @Test
    @DisplayName("AC12, S-01: a rejected target is not stored and writes URL_REJECTED with the reason")
    void rejectsDisallowedScheme() {
        var generator = new ScriptedShortCodeGenerator(List.of(FREE));

        assertThatThrownBy(() -> service(generator).create("javascript:alert(1)", OWNER, CONTEXT))
                .isInstanceOfSatisfying(
                        UrlRejectedException.class,
                        e -> assertThat(e.reason()).isEqualTo(UrlRejectionReason.SCHEME_NOT_ALLOWED));

        assertThat(links.all()).isEmpty();
        assertThat(auditSink.events()).singleElement().satisfies(event -> {
            assertThat(event.action()).isEqualTo(AuditAction.URL_REJECTED);
            assertThat(event.outcome()).isEqualTo(Outcome.REJECTED);
            assertThat(event.reasonCode()).isEqualTo("SCHEME_NOT_ALLOWED");
            assertThat(event.resourceId()).isNull();
        });
    }

    @Test
    @DisplayName("AC18, R9: an owner reads its own active link")
    void ownerReadsOwnLink() {
        Link link = givenLink(OWNER, LinkStatus.ACTIVE);

        assertThat(service(noCodes()).get(link.code().value(), OWNER, CONTEXT)).isEqualTo(link);
        assertThat(auditSink.events()).isEmpty();
    }

    @Test
    @DisplayName("AC20, S-08: reading another key's link is not found and writes ACCESS_DENIED")
    void otherOwnerCannotRead() {
        Link link = givenLink(OTHER_OWNER, LinkStatus.ACTIVE);

        assertThatThrownBy(() -> service(noCodes()).get(link.code().value(), OWNER, CONTEXT))
                .isInstanceOf(LinkNotFoundException.class);

        assertAccessDenied(link);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"unknown", "deleted", "malformed"})
    @DisplayName("AC21, R11: reading an unknown, deleted or malformed code is not found, with no audit event")
    void readNotFound(String kind) {
        String code = codeFor(kind);

        assertThatThrownBy(() -> service(noCodes()).get(code, OWNER, CONTEXT))
                .isInstanceOf(LinkNotFoundException.class);

        assertThat(auditSink.events()).isEmpty();
    }

    @Test
    @DisplayName("AC19, R10: an owner's delete soft-deletes its link and writes LINK_DELETED")
    void ownerDeletesOwnLink() {
        Link link = givenLink(OWNER, LinkStatus.ACTIVE);

        service(noCodes()).delete(link.code().value(), OWNER, CONTEXT);

        assertThat(links.findByCode(link.code())).get().extracting(Link::status).isEqualTo(LinkStatus.DELETED);
        assertThat(auditSink.events()).singleElement().satisfies(event -> {
            assertThat(event.action()).isEqualTo(AuditAction.LINK_DELETED);
            assertThat(event.outcome()).isEqualTo(Outcome.SUCCESS);
            assertThat(event.resourceType()).isEqualTo("LINK");
            assertThat(event.resourceId()).isEqualTo(link.code().value());
        });
    }

    @Test
    @DisplayName("AC20, S-08: deleting another key's link is not found, leaves it active and writes ACCESS_DENIED")
    void otherOwnerCannotDelete() {
        Link link = givenLink(OTHER_OWNER, LinkStatus.ACTIVE);

        assertThatThrownBy(() -> service(noCodes()).delete(link.code().value(), OWNER, CONTEXT))
                .isInstanceOf(LinkNotFoundException.class);

        assertThat(links.findByCode(link.code())).contains(link);
        assertAccessDenied(link);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"unknown", "deleted", "malformed"})
    @DisplayName("AC21, R11: deleting an unknown, deleted or malformed code is not found, with no audit event")
    void deleteNotFound(String kind) {
        String code = codeFor(kind);

        assertThatThrownBy(() -> service(noCodes()).delete(code, OWNER, CONTEXT))
                .isInstanceOf(LinkNotFoundException.class);

        assertThat(auditSink.events()).isEmpty();
    }

    private String codeFor(String kind) {
        return switch (kind) {
            case "unknown" -> TestCodes.random().value();
            case "deleted" -> givenLink(OWNER, LinkStatus.DELETED).code().value();
            case "malformed" -> "abc-efg";
            default -> throw new IllegalArgumentException(kind);
        };
    }

    private void assertAccessDenied(Link link) {
        assertThat(auditSink.events()).singleElement().satisfies(event -> {
            assertThat(event.action()).isEqualTo(AuditAction.ACCESS_DENIED);
            assertThat(event.outcome()).isEqualTo(Outcome.REJECTED);
            assertThat(event.actorKeyId()).isEqualTo(OWNER);
            assertThat(event.resourceType()).isEqualTo("LINK");
            assertThat(event.resourceId()).isEqualTo(link.code().value());
            assertThat(event.reasonCode()).isEqualTo("NOT_OWNER");
        });
    }

    private static ShortCodeGenerator noCodes() {
        return new ScriptedShortCodeGenerator(List.of());
    }

    private Link givenLink(UUID owner, LinkStatus status) {
        Link link = new Link(UUID.randomUUID(), TestCodes.random(), "https://example.com/own", owner, status, NOW);
        links.insertIfCodeFree(link);
        return link;
    }

    private void givenTaken(ShortCode code) {
        links.insertIfCodeFree(
                new Link(UUID.randomUUID(), code, "https://other.example", UUID.randomUUID(), LinkStatus.ACTIVE, NOW));
    }
}
