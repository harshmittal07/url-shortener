package io.github.harshmittal.urlshortener.link.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Contract for every {@link LinkRepository} adapter. */
public abstract class LinkRepositoryContract {

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

    private static Link link(ShortCode code, LinkStatus status) {
        return new Link(
                UUID.randomUUID(),
                code,
                "https://example.com/path?q=1#frag",
                UUID.randomUUID(),
                status,
                Instant.parse("2026-10-07T10:00:00.123456Z"));
    }
}
