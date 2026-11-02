package dev.ivborrezo.shoppinglist.gateway.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ivborrezo.shoppinglist.gateway.support.MutableClock;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class TokenBucketRateLimiterTest {

  private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");

  private final MutableClock clock = new MutableClock(START);

  @Test
  void consume_withinCapacity_allowsEveryRequest() {
    TokenBucketRateLimiter limiter = limiter(3, Duration.ofMinutes(1));

    consume(limiter, "client", 3);
  }

  @Test
  void consume_afterExhaustion_isRejectedWithRetryAfterRoundedUp() {
    TokenBucketRateLimiter limiter = limiter(3, Duration.ofMinutes(1));
    consume(limiter, "client", 3);

    assertThat(limiter.consume("client")).contains(Duration.ofSeconds(20));
  }

  @Test
  void consume_whenNextTokenTakesLessThanOneSecond_retriesAfterOneSecond() {
    TokenBucketRateLimiter limiter = limiter(10, Duration.ofSeconds(10));
    consume(limiter, "client", 10);
    clock.advance(Duration.ofMillis(500));

    assertThat(limiter.consume("client")).contains(Duration.ofSeconds(1));
  }

  @Test
  void consume_afterPartialRefill_rejectsWithRemainingWaitAndAllowsWhenTokenCompletes() {
    TokenBucketRateLimiter limiter = limiter(2, Duration.ofSeconds(10));
    consume(limiter, "client", 2);
    clock.advance(Duration.ofSeconds(6));

    assertThat(limiter.consume("client")).isEmpty();
    assertThat(limiter.consume("client")).contains(Duration.ofSeconds(4));

    clock.advance(Duration.ofSeconds(5));
    assertThat(limiter.consume("client")).isEmpty();
  }

  @Test
  void consume_keysAreIndependent() {
    TokenBucketRateLimiter limiter = limiter(2, Duration.ofMinutes(1));
    consume(limiter, "first", 2);

    assertThat(limiter.consume("first")).isPresent();
    assertThat(limiter.consume("second")).isEmpty();
  }

  @Test
  void purgeInactive_removesBucketsIdleForMoreThanTwoWindows() {
    TokenBucketRateLimiter limiter = limiter(5, Duration.ofMinutes(1));
    consume(limiter, "idle", 1);
    consume(limiter, "active", 1);

    clock.advance(Duration.ofMinutes(2));
    consume(limiter, "active", 1);
    clock.advance(Duration.ofMinutes(1));

    assertThat(limiter.purgeInactive()).isEqualTo(1);
    assertThat(limiter.activeBuckets()).isEqualTo(1);
    assertThat(limiter.purgeInactive()).isZero();
  }

  @Test
  void purgeInactive_whenBucketIsRefreshedBeforeRemoval_keepsItAndGrantsNoExtraBurst() {
    TokenBucketRateLimiter limiter = limiter(3, Duration.ofMinutes(1));
    consume(limiter, "refreshed", 3);
    consume(limiter, "idle", 1);

    clock.advance(Duration.ofMinutes(3));

    // El cubo inactivo vuelve a usarse justo antes de la purga: queda activo y debe conservarse.
    assertThat(limiter.consume("refreshed")).isEmpty();

    assertThat(limiter.purgeInactive()).isEqualTo(1);
    assertThat(limiter.activeBuckets()).isEqualTo(1);

    consume(limiter, "refreshed", 2);
    assertThat(limiter.consume("refreshed")).isPresent();
  }

  @Test
  void consume_whenClockGoesBack_keepsTheMostRecentRefillAndGrantsNoExtraToken() {
    AtomicReference<Instant> now = new AtomicReference<>(START);
    TokenBucketRateLimiter limiter =
        new TokenBucketRateLimiter(
            new RateLimitProperties(3, Duration.ofMinutes(1)), clockReturning(now));

    consume(limiter, "client", 3);
    now.set(START.plus(Duration.ofMinutes(3)));
    consume(limiter, "client", 1);

    // Una petición con un instante anterior no debe retrasar la última recarga.
    now.set(START);
    assertThat(limiter.consume("client")).isEmpty();

    now.set(START.plus(Duration.ofMinutes(3)));
    assertThat(limiter.consume("client")).isEmpty();
    assertThat(limiter.consume("client")).isPresent();
  }

  @Test
  void concurrentConsumption_neverGrantsMoreThanCapacity() throws Exception {
    TokenBucketRateLimiter limiter = limiter(100, Duration.ofMinutes(1));
    ExecutorService executor = Executors.newFixedThreadPool(8);
    CountDownLatch start = new CountDownLatch(1);
    List<Future<Optional<Duration>>> attempts = new ArrayList<>();

    try {
      for (int i = 0; i < 400; i++) {
        attempts.add(
            executor.submit(
                () -> {
                  start.await();
                  return limiter.consume("shared");
                }));
      }
      start.countDown();

      long allowed = 0;
      for (Future<Optional<Duration>> attempt : attempts) {
        if (attempt.get().isEmpty()) {
          allowed++;
        }
      }
      assertThat(allowed).isEqualTo(100);
    } finally {
      executor.shutdownNow();
    }
  }

  private TokenBucketRateLimiter limiter(int requests, Duration window) {
    return new TokenBucketRateLimiter(new RateLimitProperties(requests, window), clock);
  }

  private Clock clockReturning(AtomicReference<Instant> instant) {
    return new Clock() {

      @Override
      public ZoneId getZone() {
        return ZoneOffset.UTC;
      }

      @Override
      public Clock withZone(ZoneId zone) {
        return this;
      }

      @Override
      public Instant instant() {
        return instant.get();
      }
    };
  }

  private void consume(TokenBucketRateLimiter limiter, String key, int requests) {
    for (int i = 0; i < requests; i++) {
      assertThat(limiter.consume(key)).isEmpty();
    }
  }
}
