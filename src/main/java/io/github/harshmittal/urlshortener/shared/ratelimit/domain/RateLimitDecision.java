package io.github.harshmittal.urlshortener.shared.ratelimit.domain;

import java.time.Duration;

public sealed interface RateLimitDecision {

    record Allowed() implements RateLimitDecision {}

    /** @param retryAfter how long until the key may try again; always positive */
    record Rejected(Duration retryAfter) implements RateLimitDecision {}
}
