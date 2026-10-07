package io.github.harshmittal.urlshortener.link.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Contract for every {@link LinkRepository} adapter. */
public abstract class LinkRepositoryContract {

    private static final Instant DAY_START = Instant.parse("2026-10-07T00:00:00Z");
    private static final Instant NEXT_DAY_START = Instant.parse("2026-10-08T00:00:00Z");

    protected abstract LinkRepository repository();

    @Test
    @DisplayName("R7: a link with a free code is inserted and found by its code")
    void insertsAndFinds() {
        Link link = link(TestCodes.random(), LinkStatus.ACTIVE);

        assertThat(repository().insertIfCodeFree(link)).isTrue();

        assertThat(repository().findByCode(link.code())).contains(link);
    }

    @Test
    @DisplayName("R7, S-06: a taken code is reported as taken, and the first link is unchanged")
    void reportsTakenCode() {
        ShortCode code = TestCodes.random();
        Link first = link(code, LinkStatus.ACTIVE);
        repository().insertIfCodeFree(first);

        assertThat(repository().insertIfCodeFree(link(code, LinkStatus.ACTIVE))).isFalse();

        assertThat(repository().findByCode(code)).contains(first);
    }

    @Test
    @DisplayName("AC22, R10: a deleted link keeps its code, so the code is never issued again")
    void deletedLinkKeepsItsCode() {
        ShortCode code = TestCodes.random();
        repository().insertIfCodeFree(link(code, LinkStatus.DELETED));

        assertThat(repository().insertIfCodeFree(link(code, LinkStatus.ACTIVE))).isFalse();
    }

    @Test
    @DisplayName("AC19, R10: markDeleted soft-deletes an active link; the row and its code remain")
    void marksActiveLinkDeleted() {
        Link link = link(TestCodes.random(), LinkStatus.ACTIVE);
        repository().insertIfCodeFree(link);

        assertThat(repository().markDeleted(link.code())).isTrue();

        assertThat(repository().findByCode(link.code()))
                .contains(new Link(
                        link.id(),
                        link.code(),
                        link.targetUrl(),
                        link.ownerKeyId(),
                        LinkStatus.DELETED,
                        link.createdAt()));
    }

    @Test
    @DisplayName("AC21, R10: markDeleted reports false for a deleted or unknown link")
    void markDeletedOnlyAffectsActiveLinks() {
        ShortCode deleted = TestCodes.random();
        repository().insertIfCodeFree(link(deleted, LinkStatus.DELETED));

        assertThat(repository().markDeleted(deleted)).isFalse();
        assertThat(repository().markDeleted(TestCodes.random())).isFalse();
    }

    @Test
    @DisplayName("R13: an unknown code finds nothing")
    void unknownCodeFindsNothing() {
        assertThat(repository().findByCode(TestCodes.random())).isEmpty();
    }

    @Test
    @DisplayName("R2 (spec 02), S-09: the count includes the owner's deleted links")
    void countIncludesDeletedLinks() {
        UUID owner = UUID.randomUUID();
        Link kept = link(TestCodes.random(), owner, DAY_START.plusSeconds(60));
        Link deleted = link(TestCodes.random(), owner, DAY_START.plusSeconds(120));
        repository().insertIfCodeFree(kept);
        repository().insertIfCodeFree(deleted);
        repository().markDeleted(deleted.code());

        assertThat(repository().countCreatedBy(owner, DAY_START, NEXT_DAY_START))
                .isEqualTo(2);
    }

    @Test
    @DisplayName("R1 (spec 02): the count window is [from, until), to the microsecond")
    void countWindowIsHalfOpen() {
        UUID owner = UUID.randomUUID();
        Instant lastMicroOfDay = NEXT_DAY_START.minus(1, ChronoUnit.MICROS);
        for (Instant createdAt :
                List.of(DAY_START.minus(1, ChronoUnit.MICROS), DAY_START, lastMicroOfDay, NEXT_DAY_START)) {
            repository().insertIfCodeFree(link(TestCodes.random(), owner, createdAt));
        }

        assertThat(repository().countCreatedBy(owner, DAY_START, NEXT_DAY_START))
                .isEqualTo(2);
    }

    @Test
    @DisplayName("R1 (spec 02), S-08: the count covers only the given owner's links")
    void countIsPerOwner() {
        UUID owner = UUID.randomUUID();
        repository().insertIfCodeFree(link(TestCodes.random(), owner, DAY_START.plusSeconds(1)));
        repository().insertIfCodeFree(link(TestCodes.random(), UUID.randomUUID(), DAY_START.plusSeconds(1)));

        assertThat(repository().countCreatedBy(owner, DAY_START, NEXT_DAY_START))
                .isEqualTo(1);
        assertThat(repository().countCreatedBy(UUID.randomUUID(), DAY_START, NEXT_DAY_START))
                .isZero();
    }

    @Test
    @DisplayName("AC2 (spec 03), S-08: the list holds only the given owner's links; an owner with none gets none")
    void listIsPerOwner() {
        UUID owner = UUID.randomUUID();
        Link own = link(TestCodes.random(), owner, DAY_START.plusSeconds(1));
        repository().insertIfCodeFree(own);
        repository().insertIfCodeFree(link(TestCodes.random(), UUID.randomUUID(), DAY_START.plusSeconds(2)));

        assertThat(repository().findActiveByOwner(owner, 100)).containsExactly(own);
        assertThat(repository().findActiveByOwner(UUID.randomUUID(), 100)).isEmpty();
    }

    @Test
    @DisplayName("AC3 (spec 03): the list leaves out the owner's deleted links")
    void listLeavesOutDeletedLinks() {
        UUID owner = UUID.randomUUID();
        Link kept = link(TestCodes.random(), owner, DAY_START.plusSeconds(1));
        Link deleted = link(TestCodes.random(), owner, DAY_START.plusSeconds(2));
        repository().insertIfCodeFree(kept);
        repository().insertIfCodeFree(deleted);
        repository().markDeleted(deleted.code());

        assertThat(repository().findActiveByOwner(owner, 100)).containsExactly(kept);
    }

    @Test
    @DisplayName("AC4 (spec 03): newest first; a creation-time tie is ordered by code, descending binary order")
    void listIsNewestFirstWithBinaryCodeTieBreak() {
        UUID owner = UUID.randomUUID();
        String rest = TestCodes.random().value().substring(1);
        // Binary order puts 'a' (97) after 'B' (66); a linguistic collation would not.
        Link tiedUpper = link(new ShortCode("B" + rest), owner, DAY_START.plusSeconds(10));
        Link tiedLower = link(new ShortCode("a" + rest), owner, DAY_START.plusSeconds(10));
        Link oldest = link(TestCodes.random(), owner, DAY_START.plusSeconds(1));
        Link newest = link(TestCodes.random(), owner, DAY_START.plusSeconds(20));
        for (Link link : List.of(oldest, tiedUpper, newest, tiedLower)) {
            assertThat(repository().insertIfCodeFree(link)).isTrue();
        }

        assertThat(repository().findActiveByOwner(owner, 100)).containsExactly(newest, tiedLower, tiedUpper, oldest);
    }

    @Test
    @DisplayName("AC5, AC6 (spec 03): the list holds at most limit links, the newest ones")
    void listHoldsAtMostLimitNewest() {
        UUID owner = UUID.randomUUID();
        List<Link> created = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            Link link = link(TestCodes.random(), owner, DAY_START.plusSeconds(i));
            repository().insertIfCodeFree(link);
            created.add(link);
        }

        assertThat(repository().findActiveByOwner(owner, 2)).containsExactly(created.get(3), created.get(2));
        assertThat(repository().findActiveByOwner(owner, 1)).containsExactly(created.get(3));
    }

    private static Link link(ShortCode code, LinkStatus status) {
        return new Link(
                UUID.randomUUID(),
                code,
                "https://example.com/path?q=1#frag",
                UUID.randomUUID(),
                status,
                Instant.parse("2026-10-07T10:00:00.123456Z"));
    }

    private static Link link(ShortCode code, UUID owner, Instant createdAt) {
        return new Link(UUID.randomUUID(), code, "https://example.com/q", owner, LinkStatus.ACTIVE, createdAt);
    }
}
