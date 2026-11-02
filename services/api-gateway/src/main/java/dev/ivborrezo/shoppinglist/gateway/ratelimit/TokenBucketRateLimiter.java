package dev.ivborrezo.shoppinglist.gateway.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Limitador de tasa por clave con un token bucket en memoria.
 *
 * <p>Cada clave dispone de un cubo con capacidad igual al número de peticiones configurado y una
 * recarga continua de esa capacidad por ventana. El estado de cada cubo se actualiza con un bucle
 * CAS sobre una referencia atómica, sin sincronizar el mapa ni bloquear el hilo de la petición. El
 * reloj se inyecta para que las pruebas controlen la recarga.
 */
public class TokenBucketRateLimiter {

  private static final double NANOS_PER_SECOND = 1_000_000_000.0;

  private static final int MAX_PURGE_ATTEMPTS = 4;

  private final int capacity;

  private final double tokensPerNano;

  private final long inactivityNanos;

  private final Clock clock;

  private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

  /**
   * Crea el limitador a partir de la configuración del borde.
   *
   * @param properties capacidad y ventana del límite
   * @param clock reloj con el que se mide la recarga
   */
  public TokenBucketRateLimiter(RateLimitProperties properties, Clock clock) {
    this.capacity = properties.requests();
    this.tokensPerNano = (double) capacity / properties.window().toNanos();
    this.inactivityNanos = properties.window().multipliedBy(2).toNanos();
    this.clock = clock;
  }

  /**
   * Intenta consumir un token del cubo de la clave.
   *
   * @param key identidad del cliente
   * @return vacío si la petición puede continuar; si no, los segundos hasta el siguiente token,
   *     redondeados hacia arriba y con un mínimo de uno
   */
  public Optional<Duration> consume(String key) {
    long nowNanos = nowNanos();
    Bucket bucket = buckets.computeIfAbsent(key, ignored -> new Bucket(capacity, nowNanos));
    return tryConsume(bucket, nowNanos);
  }

  /**
   * Elimina los cubos que llevan sin uso más de dos ventanas.
   *
   * <p>El estado de cada cubo se revalida justo antes de borrarlo: si se ha refrescado durante el
   * barrido, el cubo se conserva para que no se recreen con la capacidad completa y concedan una
   * ráfaga extra.
   *
   * @return número de cubos eliminados
   */
  public int purgeInactive() {
    long nowNanos = nowNanos();
    int before = buckets.size();
    for (String key : buckets.keySet()) {
      purgeBucket(key, nowNanos);
    }
    return before - buckets.size();
  }

  /**
   * Número de cubos mantenidos en memoria.
   *
   * @return tamaño actual del mapa de cubos
   */
  public int activeBuckets() {
    return buckets.size();
  }

  private void purgeBucket(String key, long nowNanos) {
    for (int attempt = 0; attempt < MAX_PURGE_ATTEMPTS; attempt++) {
      Bucket bucket = buckets.get(key);
      if (bucket == null) {
        return;
      }
      BucketState observed = bucket.state.get();
      if (!isInactive(observed, nowNanos)) {
        return;
      }
      if (!bucket.state.get().equals(observed)) {
        continue;
      }
      buckets.remove(key, bucket);
      return;
    }
  }

  private boolean isInactive(BucketState state, long nowNanos) {
    return nowNanos - state.lastRefillNanos() > inactivityNanos;
  }

  private Optional<Duration> tryConsume(Bucket bucket, long nowNanos) {
    while (true) {
      BucketState current = bucket.state.get();
      long refillNanos = Math.max(nowNanos, current.lastRefillNanos());
      double tokens = refill(current, refillNanos);
      if (tokens >= 1.0) {
        if (bucket.state.compareAndSet(current, new BucketState(tokens - 1.0, refillNanos))) {
          return Optional.empty();
        }
      } else if (bucket.state.compareAndSet(current, new BucketState(tokens, refillNanos))) {
        return Optional.of(retryAfter(tokens));
      }
    }
  }

  private double refill(BucketState state, long nowNanos) {
    long elapsedNanos = Math.max(0L, nowNanos - state.lastRefillNanos());
    return Math.min(capacity, state.tokens() + elapsedNanos * tokensPerNano);
  }

  private Duration retryAfter(double tokens) {
    long waitNanos = (long) Math.ceil((1.0 - tokens) / tokensPerNano);
    long seconds = (long) Math.ceil(waitNanos / NANOS_PER_SECOND);
    return Duration.ofSeconds(Math.max(1L, seconds));
  }

  private long nowNanos() {
    Instant now = clock.instant();
    return now.getEpochSecond() * 1_000_000_000L + now.getNano();
  }

  private static final class Bucket {

    private final AtomicReference<BucketState> state;

    private Bucket(double tokens, long lastRefillNanos) {
      state = new AtomicReference<>(new BucketState(tokens, lastRefillNanos));
    }
  }

  private record BucketState(double tokens, long lastRefillNanos) {}
}
