package dev.ivborrezo.shoppinglist.gateway.ratelimit;

import java.time.Duration;
import java.util.Optional;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Aplica el rate limiting a todas las peticiones de API que entran por el borde.
 *
 * <p>Se ejecuta después de la correlación para que el rechazo pueda ecoar el identificador, y antes
 * del enrutado para que el límite cubra también los paths no expuestos. El preflight CORS y la
 * superficie de actuator no consumen cupo. Cuando el cubo de la clave no tiene tokens, lanza {@link
 * RateLimitExceededException} y deja la respuesta al escritor único de errores.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 15)
public class RateLimitWebFilter implements WebFilter {

  private static final String ACTUATOR_PATH = "/actuator";

  private final TokenBucketRateLimiter rateLimiter;

  private final RateLimitKeyResolver keyResolver;

  /**
   * Inyecta el limitador y la resolución de la clave del cliente.
   *
   * @param rateLimiter limitador compartido del borde
   * @param keyResolver resolución de la identidad de rate limiting
   */
  public RateLimitWebFilter(TokenBucketRateLimiter rateLimiter, RateLimitKeyResolver keyResolver) {
    this.rateLimiter = rateLimiter;
    this.keyResolver = keyResolver;
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    ServerHttpRequest request = exchange.getRequest();
    if (isExcluded(request)) {
      return chain.filter(exchange);
    }
    Optional<Duration> retryAfter = rateLimiter.consume(keyResolver.resolve(exchange));
    if (retryAfter.isPresent()) {
      return Mono.error(new RateLimitExceededException(retryAfter.get()));
    }
    return chain.filter(exchange);
  }

  private boolean isExcluded(ServerHttpRequest request) {
    return isPreflight(request) || isActuator(request);
  }

  private boolean isPreflight(ServerHttpRequest request) {
    HttpHeaders headers = request.getHeaders();
    return request.getMethod() == HttpMethod.OPTIONS
        && headers.containsHeader(HttpHeaders.ORIGIN)
        && headers.containsHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD);
  }

  private boolean isActuator(ServerHttpRequest request) {
    String path = request.getPath().value();
    return path.equals(ACTUATOR_PATH) || path.startsWith(ACTUATOR_PATH + "/");
  }
}
