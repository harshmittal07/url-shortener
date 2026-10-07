package io.github.harshmittal.urlshortener.shared.ratelimit.adapter.out.bucket4j;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.TimeMeter;
import io.github.harshmittal.urlshortener.shared.ratelimit.domain.RateLimitDecision;
import io.github.harshmittal.urlshortener.shared.ratelimit.domain.RateLimiter;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory Bucket4j buckets, one per key (D2, S-09). Each bucket refills completely once a minute
 * after its first attempt, so a key gets the limit per minute and no more. Time comes from the
 * injected {@link Clock}. Buckets live per instance (SECURITY.md §10, spec L3) and are kept for the
 * life of the process; keys are API key IDs, so their number is bounded by the issued keys.
 */
public final class Bucket4jRateLimiter implements RateLimiter {

    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final int limitPerMinute;
    private final TimeMeter timeMeter;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public Bucket4jRateLimiter(int limitPerMinute, Clock clock) {
        if (limitPerMinute < 1) {
            throw new IllegalArgumentException("The rate limit must be at least 1 per minute");
        }
        this.limitPerMinute = limitPerMinute;
        this.timeMeter = new ClockTimeMeter(clock);
    }

    @Override
    public RateLimitDecision tryConsume(String key) {
        ConsumptionProbe probe =
                buckets.computeIfAbsent(key, ignored -> newBucket()).tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            return new RateLimitDecision.Allowed();
        }
        return new RateLimitDecision.Rejected(Duration.ofNanos(probe.getNanosToWaitForRefill()));
    }

    private Bucket newBucket() {
        return Bucket.builder()
                .addLimit(limit -> limit.capacity(limitPerMinute).refillIntervally(limitPerMinute, WINDOW))
                .withCustomTimePrecision(timeMeter)
                .build();
    }

    private record ClockTimeMeter(Clock clock) implements TimeMeter {
        @Override
        public long currentTimeNanos() {
            Instant now = clock.instant();
            return Math.addExact(Math.multiplyExact(now.getEpochSecond(), 1_000_000_000L), now.getNano());
        }

        @Override
        public boolean isWallClockBased() {
            return true;
        }
    }
}
