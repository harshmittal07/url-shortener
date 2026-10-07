package io.github.harshmittal.urlshortener.shared.ratelimit.domain;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.harshmittal.urlshortener.shared.ratelimit.domain.RateLimitDecision.Allowed;
import io.github.harshmittal.urlshortener.shared.ratelimit.domain.RateLimitDecision.Rejected;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Contract for every {@link RateLimiter} adapter: a fixed number of attempts per key per minute. */
public abstract class RateLimiterContract {

    private static final int LIMIT = 3;

    protected final SettableClock clock = new SettableClock(Instant.parse("2026-10-07T10:00:00Z"));

    protected abstract RateLimiter rateLimiter(int limitPerMinute, SettableClock clock);

    @Test
    @DisplayName("S-09: attempts up to the limit are allowed")
    void allowsUpToTheLimit() {
        RateLimiter limiter = rateLimiter(LIMIT, clock);

        for (int attempt = 1; attempt <= LIMIT; attempt++) {
            assertThat(limiter.tryConsume("key-a")).isInstanceOf(Allowed.class);
        }
    }

    @Test
    @DisplayName("AC26, S-09: the attempt after the limit is rejected with a Retry-After of at most one minute")
    void rejectsBeyondTheLimit() {
        RateLimiter limiter = exhausted("key-a");

        assertThat(limiter.tryConsume("key-a"))
                .isInstanceOfSatisfying(
                        Rejected.class,
                        rejected -> assertThat(rejected.retryAfter())
                                .isPositive()
                                .isLessThanOrEqualTo(Duration.ofMinutes(1)));
    }

    @Test
    @DisplayName("AC27: the configured limit applies")
    void configuredLimitApplies() {
        RateLimiter limiter = rateLimiter(1, clock);

        assertThat(limiter.tryConsume("key-a")).isInstanceOf(Allowed.class);
        assertThat(limiter.tryConsume("key-a")).isInstanceOf(Rejected.class);
    }

    @Test
    @DisplayName("AC28, S-09: one key exhausting its limit does not limit another key")
    void keysAreIndependent() {
        RateLimiter limiter = exhausted("key-a");

        assertThat(limiter.tryConsume("key-b")).isInstanceOf(Allowed.class);
    }

    @Test
    @DisplayName("R15: within the minute the key stays rejected, and Retry-After counts down with the clock")
    void staysRejectedWithinTheMinute() {
        RateLimiter limiter = exhausted("key-a");

        clock.advance(Duration.ofSeconds(35));

        assertThat(limiter.tryConsume("key-a"))
                .isInstanceOfSatisfying(
                        Rejected.class,
                        rejected -> assertThat(rejected.retryAfter()).isEqualTo(Duration.ofSeconds(25)));
    }

    @Test
    @DisplayName("R15: once the minute has passed, the key may make attempts again")
    void allowsAgainAfterTheMinute() {
        RateLimiter limiter = exhausted("key-a");

        clock.advance(Duration.ofMinutes(1));

        for (int attempt = 1; attempt <= LIMIT; attempt++) {
            assertThat(limiter.tryConsume("key-a")).isInstanceOf(Allowed.class);
        }
        assertThat(limiter.tryConsume("key-a")).isInstanceOf(Rejected.class);
    }

    private RateLimiter exhausted(String key) {
        RateLimiter limiter = rateLimiter(LIMIT, clock);
        for (int attempt = 1; attempt <= LIMIT; attempt++) {
            limiter.tryConsume(key);
        }
        return limiter;
    }
}
