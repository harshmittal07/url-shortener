package io.github.harshmittal.urlshortener.link.adapter.in.web;

import java.time.Duration;

/** The caller is over its link-creation limit (R16). */
public final class CreationRateLimitedException extends RuntimeException {

    private final Duration retryAfter;

    public CreationRateLimitedException(Duration retryAfter) {
        super("Link creation limit exceeded", null, false, false);
        this.retryAfter = retryAfter;
    }

    /** Whole seconds for {@code Retry-After}, rounded up and at least 1. */
    public long retryAfterSeconds() {
        return Math.max(1, (retryAfter.toMillis() + 999) / 1000);
    }
}
