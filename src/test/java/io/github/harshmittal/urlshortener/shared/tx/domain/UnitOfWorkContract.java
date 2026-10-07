package io.github.harshmittal.urlshortener.shared.tx.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Contract for every transactional {@link UnitOfWork} adapter. */
public abstract class UnitOfWorkContract {

    protected abstract UnitOfWork unitOfWork();

    /** Writes a durable marker through the adapter's transactional resource. */
    protected abstract void writeMarker(String marker);

    protected abstract boolean markerExists(String marker);

    @Test
    @DisplayName("R18: work commits when it completes")
    void commits() {
        String marker = newMarker();

        unitOfWork().inTransaction(() -> {
            writeMarker(marker);
            return null;
        });

        assertThat(markerExists(marker)).isTrue();
    }

    @Test
    @DisplayName("R18: work rolls back when it throws, and the exception propagates")
    void rollsBackOnException() {
        String marker = newMarker();

        assertThatThrownBy(() -> unitOfWork().inTransaction(() -> {
                    writeMarker(marker);
                    throw new IllegalStateException("fail");
                }))
                .isInstanceOf(IllegalStateException.class);

        assertThat(markerExists(marker)).isFalse();
    }

    @Test
    @DisplayName("R18: nested work joins the enclosing transaction and rolls back with it")
    void nestedWorkJoinsEnclosingTransaction() {
        String marker = newMarker();

        assertThatThrownBy(() -> unitOfWork().inTransaction(() -> {
                    unitOfWork().inTransaction(() -> {
                        writeMarker(marker);
                        return null;
                    });
                    throw new IllegalStateException("fail");
                }))
                .isInstanceOf(IllegalStateException.class);

        assertThat(markerExists(marker)).isFalse();
    }

    @Test
    @DisplayName("R20: requiresNew work commits even when the enclosing transaction rolls back")
    void requiresNewSurvivesOuterRollback() {
        String marker = newMarker();

        assertThatThrownBy(() -> unitOfWork().inTransaction(() -> {
                    unitOfWork().requiresNew(() -> writeMarker(marker));
                    throw new IllegalStateException("fail");
                }))
                .isInstanceOf(IllegalStateException.class);

        assertThat(markerExists(marker)).isTrue();
    }

    private static String newMarker() {
        return "uow-" + UUID.randomUUID();
    }
}
