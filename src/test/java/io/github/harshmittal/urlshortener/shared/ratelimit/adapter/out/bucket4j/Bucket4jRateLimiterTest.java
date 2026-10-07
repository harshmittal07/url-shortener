package io.github.harshmittal.urlshortener.shared.ratelimit.adapter.out.bucket4j;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.harshmittal.urlshortener.shared.ratelimit.domain.RateLimiter;
import io.github.harshmittal.urlshortener.shared.ratelimit.domain.RateLimiterContract;
import io.github.harshmittal.urlshortener.shared.ratelimit.domain.SettableClock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class Bucket4jRateLimiterTest extends RateLimiterContract {

    @Override
    protected RateLimiter rateLimiter(int limitPerMinute, SettableClock clock) {
        return new Bucket4jRateLimiter(limitPerMinute, clock);
    }

    @Test
    @DisplayName("R15: a limit below 1 is refused at startup")
    void refusesNonPositiveLimit() {
        assertThatThrownBy(() -> new Bucket4jRateLimiter(0, clock)).isInstanceOf(IllegalArgumentException.class);
    }
}
