package dev.ivborrezo.shoppinglist.gateway.ratelimit;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuración del rate limiting del borde.
 *
 * <p>La capacidad es el número de peticiones que un cliente puede acumular como ráfaga y la ventana
 * el periodo en el que se repone esa capacidad de forma continua.
 *
 * @param requests peticiones permitidas por ventana, mayor que cero
 * @param window ventana de recarga, mayor que cero
 */
@ConfigurationProperties("api-gateway.rate-limit")
public record RateLimitProperties(int requests, Duration window) {

  /**
   * Valida que la capacidad y la ventana sean positivas.
   *
   * @throws IllegalArgumentException si la capacidad o la ventana no son positivas
   */
  public RateLimitProperties {
    if (requests <= 0) {
      throw new IllegalArgumentException("api-gateway.rate-limit.requests must be positive");
    }
    if (window == null || window.isZero() || window.isNegative()) {
      throw new IllegalArgumentException("api-gateway.rate-limit.window must be positive");
    }
  }
}
