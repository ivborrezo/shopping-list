package dev.ivborrezo.shoppinglist.gateway.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ivborrezo.shoppinglist.gateway.support.MutableClock;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class RateLimitBucketCleanerTest {

  private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");

  private final MutableClock clock = new MutableClock(START);

  @Test
  void purgeInactiveBuckets_removesIdleBucketsAndKeepsRefreshedOnes() {
    TokenBucketRateLimiter limiter =
        new TokenBucketRateLimiter(new RateLimitProperties(5, Duration.ofMinutes(1)), clock);

    limiter.consume("idle");
    limiter.consume("active");

    clock.advance(Duration.ofMinutes(2));
    limiter.consume("active");
    clock.advance(Duration.ofMinutes(1));

    assertThat(limiter.activeBuckets()).isEqualTo(2);

    RateLimitBucketCleaner cleaner = new RateLimitBucketCleaner(limiter);
    cleaner.purgeInactiveBuckets();

    assertThat(limiter.activeBuckets()).isEqualTo(1);
  }
}
