package io.github.harshmittal.urlshortener.link.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.harshmittal.urlshortener.shared.audit.domain.AuditAction;
import io.github.harshmittal.urlshortener.shared.audit.domain.AuditContext;
import io.github.harshmittal.urlshortener.shared.audit.domain.AuditEvent;
import io.github.harshmittal.urlshortener.shared.audit.domain.AuditSink;
import io.github.harshmittal.urlshortener.shared.audit.domain.AuditTrail;
import io.github.harshmittal.urlshortener.shared.audit.domain.FailingAuditSink;
import io.github.harshmittal.urlshortener.shared.audit.domain.InMemoryAuditSink;
import io.github.harshmittal.urlshortener.shared.audit.domain.Outcome;
import io.github.harshmittal.urlshortener.shared.ratelimit.domain.SettableClock;
import io.github.harshmittal.urlshortener.shared.tx.domain.InlineUnitOfWork;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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

    /** Spec 01 tests run with no effective daily quota; {@link DailyQuota} covers spec 02. */
    private LinkService service(ShortCodeGenerator codes) {
        return service(codes, clock, auditSink, Integer.MAX_VALUE);
    }

    private LinkService service(ShortCodeGenerator codes, Clock clock, AuditSink sink, int dailyQuota) {
        return new LinkService(
                links,
                codes,
                new StandardUrlPolicy("https://sho.rt"),
                new AuditTrail(sink, new InlineUnitOfWork(), clock, UUID::randomUUID),
                new InlineUnitOfWork(),
                clock,
                UUID::randomUUID,
                dailyQuota);
    }

    @Test
    @DisplayName("R11 (spec 02): a daily quota below 1 is refused")
    void rejectsQuotaBelowOne() {
        assertThatThrownBy(() -> service(noCodes(), clock, auditSink, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    /** Spec 02: the daily link quota per API key, set to 3 here. */
    @Nested
    class DailyQuota {

        private static final int QUOTA = 3;
        private static final Instant DAY_START = Instant.parse("2026-10-07T00:00:00Z");
        private static final Instant LAST_MICRO_OF_DAY = Instant.parse("2026-10-07T23:59:59.999999Z");
        private static final Instant NEXT_DAY_START = Instant.parse("2026-10-08T00:00:00Z");

        private final SettableClock quotaClock = new SettableClock(Instant.parse("2026-10-07T12:00:00Z"));

        private LinkService quotaService(ShortCodeGenerator codes) {
            return service(codes, quotaClock, auditSink, QUOTA);
        }

        @Test
        @DisplayName("AC1 (spec 02), S-09: at the quota a create is refused, stores nothing and writes RATE_LIMITED")
        void refusesAtTheQuota() {
            givenLinksAt(OWNER, QUOTA, DAY_START.plusSeconds(60));

            assertThatThrownBy(() -> quotaService(noCodes()).create("https://example.com/x", OWNER, CONTEXT))
                    .isInstanceOf(DailyQuotaExceededException.class);

            assertThat(links.all()).hasSize(QUOTA);
            assertThat(auditSink.events()).singleElement().satisfies(event -> {
                assertThat(event.action()).isEqualTo(AuditAction.RATE_LIMITED);
                assertThat(event.outcome()).isEqualTo(Outcome.REJECTED);
                assertThat(event.actorKeyId()).isEqualTo(OWNER);
                assertThat(event.resourceType()).isEqualTo("LINK");
                assertThat(event.resourceId()).isNull();
                assertThat(event.reasonCode()).isEqualTo("DAILY_QUOTA");
            });
        }

        @Test
        @DisplayName("AC2 (spec 02): the quota-th link of the day is allowed")
        void allowsTheLastLinkOfTheQuota() {
            givenLinksAt(OWNER, QUOTA - 1, DAY_START);

            Link link = quotaService(new ScriptedShortCodeGenerator(List.of(FREE)))
                    .create("https://example.com/x", OWNER, CONTEXT);

            assertThat(link.code()).isEqualTo(FREE);
            assertThat(links.all()).hasSize(QUOTA);
        }

        @Test
        @DisplayName("AC3 (spec 02), S-09: deleted links still count toward the quota")
        void deletedLinksCount() {
            List<Link> created = givenLinksAt(OWNER, QUOTA, DAY_START.plusSeconds(60));
            links.markDeleted(created.get(0).code());
            links.markDeleted(created.get(1).code());

            assertThatThrownBy(() -> quotaService(noCodes()).create("https://example.com/x", OWNER, CONTEXT))
                    .isInstanceOf(DailyQuotaExceededException.class);
        }

        @Test
        @DisplayName("AC5 (spec 02): links from the previous UTC day do not count at 00:00:00 UTC")
        void previousDayDoesNotCount() {
            givenLinksAt(OWNER, QUOTA, LAST_MICRO_OF_DAY);
            quotaClock.set(NEXT_DAY_START);

            Link link = quotaService(new ScriptedShortCodeGenerator(List.of(FREE)))
                    .create("https://example.com/x", OWNER, CONTEXT);

            assertThat(link.createdAt()).isEqualTo(NEXT_DAY_START);
        }

        @Test
        @DisplayName("AC6, AC9 (spec 02): refused at 23:59:59.999999 with Retry-After 1, allowed at 00:00:00")
        void dayEndsAtMidnightUtc() {
            givenLinksAt(OWNER, QUOTA, DAY_START);
            quotaClock.set(LAST_MICRO_OF_DAY);

            assertThatThrownBy(() -> quotaService(noCodes()).create("https://example.com/x", OWNER, CONTEXT))
                    .isInstanceOfSatisfying(
                            DailyQuotaExceededException.class,
                            e -> assertThat(e.retryAfterSeconds()).isEqualTo(1));

            quotaClock.set(NEXT_DAY_START);
            assertThat(quotaService(new ScriptedShortCodeGenerator(List.of(FREE)))
                            .create("https://example.com/x", OWNER, CONTEXT)
                            .code())
                    .isEqualTo(FREE);
        }

        @Test
        @DisplayName("AC8 (spec 02): Retry-After is the whole seconds to the next 00:00 UTC, rounded up")
        void retryAfterRunsToMidnight() {
            givenLinksAt(OWNER, QUOTA, DAY_START);
            quotaClock.set(Instant.parse("2026-10-07T22:00:00.250Z"));

            assertThatThrownBy(() -> quotaService(noCodes()).create("https://example.com/x", OWNER, CONTEXT))
                    .isInstanceOfSatisfying(
                            DailyQuotaExceededException.class,
                            e -> assertThat(e.retryAfterSeconds()).isEqualTo(7200));
        }

        @Test
        @DisplayName("AC7 (spec 02), S-08: another key's links do not use the caller's quota")
        void quotaIsPerKey() {
            givenLinksAt(OTHER_OWNER, QUOTA, DAY_START);

            assertThat(quotaService(new ScriptedShortCodeGenerator(List.of(FREE)))
                            .create("https://example.com/x", OWNER, CONTEXT)
                            .ownerKeyId())
                    .isEqualTo(OWNER);
        }

        @Test
        @DisplayName("AC11 (spec 02): an audit failure does not change the refusal")
        void auditFailureStillRefuses() {
            givenLinksAt(OWNER, QUOTA, DAY_START);
            LinkService failingAudit = service(noCodes(), quotaClock, new FailingAuditSink(), QUOTA);

            assertThatThrownBy(() -> failingAudit.create("https://example.com/x", OWNER, CONTEXT))
                    .isInstanceOf(DailyQuotaExceededException.class);
            assertThat(links.all()).hasSize(QUOTA);
        }

        @Test
        @DisplayName("AC14 (spec 02): the quota is checked before the URL policy; no URL_REJECTED")
        void quotaBeforeUrlPolicy() {
            givenLinksAt(OWNER, QUOTA, DAY_START);

            assertThatThrownBy(() -> quotaService(noCodes()).create("javascript:alert(1)", OWNER, CONTEXT))
                    .isInstanceOf(DailyQuotaExceededException.class);

            assertThat(auditSink.events()).extracting(AuditEvent::action).containsExactly(AuditAction.RATE_LIMITED);
        }

        private List<Link> givenLinksAt(UUID owner, int count, Instant createdAt) {
            List<Link> created = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                Link link = new Link(
                        UUID.randomUUID(),
                        TestCodes.random(),
                        "https://example.com/q",
                        owner,
                        LinkStatus.ACTIVE,
                        createdAt);
                links.insertIfCodeFree(link);
                created.add(link);
            }
            return created;
        }
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

    /** Spec 03: list the caller's links. */
    @Nested
    class ListLinks {

        @Test
        @DisplayName("AC1, AC3 (spec 03), S-08: the owner gets its own active links, newest first, up to the limit")
        void listsOwnActiveLinksNewestFirst() {
            Link older = givenLinkAt(OWNER, LinkStatus.ACTIVE, NOW.minusSeconds(60));
            Link newer = givenLinkAt(OWNER, LinkStatus.ACTIVE, NOW);
            givenLinkAt(OWNER, LinkStatus.DELETED, NOW.plusSeconds(1));
            givenLinkAt(OTHER_OWNER, LinkStatus.ACTIVE, NOW.plusSeconds(2));

            assertThat(service(noCodes()).list(OWNER, LinkService.MAX_LIST_LIMIT))
                    .containsExactly(newer, older);
            assertThat(service(noCodes()).list(OWNER, 1)).containsExactly(newer);
        }

        @ParameterizedTest(name = "limit {0}")
        @ValueSource(ints = {0, -1, LinkService.MAX_LIST_LIMIT + 1})
        @DisplayName("R4 (spec 03): a limit outside 1 to 100 is refused")
        void refusesLimitOutOfRange(int limit) {
            assertThatThrownBy(() -> service(noCodes()).list(OWNER, limit))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("AC10 (spec 03), S-10: listing writes no audit event")
        void listingWritesNoAuditEvent() {
            givenLinkAt(OWNER, LinkStatus.ACTIVE, NOW);

            service(noCodes()).list(OWNER, LinkService.DEFAULT_LIST_LIMIT);

            assertThat(auditSink.events()).isEmpty();
        }

        private Link givenLinkAt(UUID owner, LinkStatus status, Instant createdAt) {
            Link link = new Link(
                    UUID.randomUUID(), TestCodes.random(), "https://example.com/own", owner, status, createdAt);
            links.insertIfCodeFree(link);
            return link;
        }
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
