package dev.ivborrezo.shoppinglist.gateway.ratelimit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Purga periódicamente los cubos de rate limiting que han quedado inactivos.
 *
 * <p>La tarea se ejecuta con un retardo fijo igual a la ventana configurada y comparte el
 * planificador de un solo hilo de la aplicación. Tras cada purga registra a nivel debug el número
 * de cubos que siguen en memoria.
 */
@Component
public class RateLimitBucketCleaner {

  private static final Logger log = LoggerFactory.getLogger(RateLimitBucketCleaner.class);

  private final TokenBucketRateLimiter rateLimiter;

  /**
   * Inyecta el limitador cuyos cubos se purgan.
   *
   * @param rateLimiter limitador compartido del borde
   */
  public RateLimitBucketCleaner(TokenBucketRateLimiter rateLimiter) {
    this.rateLimiter = rateLimiter;
  }

  /** Purga los cubos sin uso durante más de dos ventanas. */
  @Scheduled(fixedDelayString = "${api-gateway.rate-limit.window}")
  public void purgeInactiveBuckets() {
    rateLimiter.purgeInactive();
    log.debug("Rate limit buckets in memory: {}", rateLimiter.activeBuckets());
  }
}
