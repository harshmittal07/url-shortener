package io.github.harshmittal.urlshortener.link.domain;

import java.time.Duration;

/** The caller has used its daily link quota (spec 02 R5); it may retry at the next 00:00 UTC. */
public final class DailyQuotaExceededException extends RuntimeException {

    private final Duration retryAfter;

    public DailyQuotaExceededException(Duration retryAfter) {
        super("Daily link quota exceeded", null, false, false);
        this.retryAfter = retryAfter;
    }

    /** Whole seconds for {@code Retry-After}, rounded up and at least 1 (spec 02 R6). */
    public long retryAfterSeconds() {
        return Math.max(1, (retryAfter.toNanos() + 999_999_999) / 1_000_000_000);
    }
}
