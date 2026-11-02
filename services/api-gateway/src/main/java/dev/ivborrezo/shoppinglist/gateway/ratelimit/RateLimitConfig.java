package dev.ivborrezo.shoppinglist.gateway.ratelimit;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Infraestructura del rate limiting del borde.
 *
 * <p>Registra las propiedades tipadas del límite, el reloj del sistema y el limitador compartido, y
 * activa la planificación que purga los cubos inactivos.
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitConfig {

  /**
   * Reloj del sistema con el que se mide la recarga de los cubos.
   *
   * @return reloj UTC de producción
   */
  @Bean
  @Primary
  Clock clock() {
    return Clock.systemUTC();
  }

  /**
   * Crea el limitador compartido por el filtro del borde.
   *
   * @param properties configuración de capacidad y ventana
   * @param clock reloj con el que se mide la recarga
   * @return limitador en memoria
   */
  @Bean
  TokenBucketRateLimiter rateLimiter(RateLimitProperties properties, Clock clock) {
    return new TokenBucketRateLimiter(properties, clock);
  }
}
