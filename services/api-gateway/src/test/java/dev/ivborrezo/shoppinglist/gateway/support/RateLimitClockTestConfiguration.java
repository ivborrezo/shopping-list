package dev.ivborrezo.shoppinglist.gateway.support;

import dev.ivborrezo.shoppinglist.gateway.ratelimit.RateLimitProperties;
import dev.ivborrezo.shoppinglist.gateway.ratelimit.TokenBucketRateLimiter;
import java.time.Instant;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Configuración de test que conserva la resolución real de la clave del límite.
 *
 * <p>Sustituye solo el reloj y el limitador, de modo que las pruebas ejerciten la resolución de
 * producción ({@code user:<uuid>} con caída a {@code ip:<dirección>}) con una recarga de tokens
 * determinista. Se importa únicamente desde las pruebas que necesitan esa costura.
 */
@TestConfiguration
public class RateLimitClockTestConfiguration {

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
}
