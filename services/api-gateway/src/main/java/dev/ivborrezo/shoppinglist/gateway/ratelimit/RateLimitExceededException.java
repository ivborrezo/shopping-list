package dev.ivborrezo.shoppinglist.gateway.ratelimit;

import java.time.Duration;

/**
 * Rechazo del rate limiting del borde.
 *
 * <p>Transporta el tiempo de espera hasta el siguiente token; el escritor único de errores lo
 * traduce a la cabecera {@code Retry-After} y al cuerpo {@code ProblemDetail}. La excepción no
 * escribe la respuesta.
 */
public class RateLimitExceededException extends RuntimeException {

  private final long retryAfterSeconds;

  /**
   * Crea el rechazo con el tiempo de espera indicado.
   *
   * @param retryAfter espera hasta el siguiente token
   */
  public RateLimitExceededException(Duration retryAfter) {
    super("Rate limit exceeded");
    this.retryAfterSeconds = toWholeSeconds(retryAfter);
  }

  /**
   * Devuelve la espera hasta el siguiente token en segundos.
   *
   * @return segundos de espera, redondeados hacia arriba y con un mínimo de uno
   */
  public long getRetryAfterSeconds() {
    return retryAfterSeconds;
  }

  private static long toWholeSeconds(Duration duration) {
    long seconds = duration.getSeconds();
    if (duration.getNano() > 0) {
      seconds++;
    }
    return Math.max(1L, seconds);
  }
}
