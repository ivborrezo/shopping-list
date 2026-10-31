package dev.ivborrezo.shoppinglist.gateway.support;

import dev.ivborrezo.shoppinglist.gateway.ratelimit.RateLimitKeyResolver;
import dev.ivborrezo.shoppinglist.gateway.ratelimit.RateLimitProperties;
import dev.ivborrezo.shoppinglist.gateway.ratelimit.TokenBucketRateLimiter;
import java.time.Instant;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Configuración de test del rate limiting.
 *
 * <p>Sustituye el limitador por la misma clase con un reloj avanzable y la clave de producción por
 * una cabecera de test, de modo que cada prueba aísle su estado en memoria. Se importa solo desde
 * las pruebas que necesitan la costura; el resolver y el reloj de producción no se alteran.
 */
@TestConfiguration
public class RateLimitTestConfiguration {

  /** Cabecera que identifica al cliente en las pruebas del límite. */
  public static final String TEST_CLIENT_HEADER = "X-Test-Client-Id";

  private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");

  /**
   * Reloj avanzable compartido por el limitador de test.
   *
   * @return reloj de test en un instante fijo
   */
  @Bean
  MutableClock rateLimitTestClock() {
    return new MutableClock(START);
  }

  /**
   * Limitador de producción con el reloj avanzable de test.
   *
   * @param properties configuración del límite cargada por la prueba
   * @param clock reloj avanzable de test
   * @return limitador de test
   */
  @Bean
  @Primary
  TokenBucketRateLimiter testRateLimiter(RateLimitProperties properties, MutableClock clock) {
    return new TokenBucketRateLimiter(properties, clock);
  }

  /**
   * Clave de test leída de una cabecera, para aislar el estado entre pruebas.
   *
   * @return resolver de clave por cabecera de test
   */
  @Bean
  @Primary
  RateLimitKeyResolver testRateLimitKeyResolver() {
    return exchange -> {
      String clientId = exchange.getRequest().getHeaders().getFirst(TEST_CLIENT_HEADER);
      return clientId != null ? clientId : "missing-client-id";
    };
  }
}
