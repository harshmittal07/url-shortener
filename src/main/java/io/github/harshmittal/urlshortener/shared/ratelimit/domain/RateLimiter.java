package io.github.harshmittal.urlshortener.shared.ratelimit.domain;

/** Counts attempts per key against a fixed limit per minute (S-09). */
public interface RateLimiter {

    /** Counts one attempt for {@code key} and says whether it is within the limit. */
    RateLimitDecision tryConsume(String key);
}
