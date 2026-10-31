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
   * @return número de cubos eliminados
   */
  public int purgeInactive() {
    long nowNanos = nowNanos();
    int before = buckets.size();
    buckets
        .entrySet()
        .removeIf(entry -> nowNanos - entry.getValue().lastRefillNanos() > inactivityNanos);
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

  private Optional<Duration> tryConsume(Bucket bucket, long nowNanos) {
    while (true) {
      BucketState current = bucket.state.get();
      double tokens = refill(current, nowNanos);
      if (tokens >= 1.0) {
        if (bucket.state.compareAndSet(current, new BucketState(tokens - 1.0, nowNanos))) {
          return Optional.empty();
        }
      } else if (bucket.state.compareAndSet(current, new BucketState(tokens, nowNanos))) {
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

    private long lastRefillNanos() {
      return state.get().lastRefillNanos();
    }
  }

  private record BucketState(double tokens, long lastRefillNanos) {}
}
