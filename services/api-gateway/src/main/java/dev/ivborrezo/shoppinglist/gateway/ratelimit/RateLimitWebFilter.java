package dev.ivborrezo.shoppinglist.gateway.ratelimit;

import java.time.Duration;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Aplica el rate limiting a las peticiones que atraviesan las cadenas de seguridad.
 *
 * <p>Se ejecuta dentro de cada cadena de seguridad, inmediatamente después de la autenticación y
 * del filtro que publica el usuario, de modo que la clave sea {@code user:<uuid>} cuando hay
 * identidad verificada y {@code ip:<dirección>} en el tráfico anónimo. El preflight CORS y la
 * superficie de actuator no consumen cupo. Cuando el cubo de la clave no tiene tokens, lanza {@link
 * RateLimitExceededException} y deja la respuesta al escritor único de errores. Cuando evalúa una
 * petición marca un atributo en el intercambio para que el rechazo posterior por credenciales
 * inválidas no vuelva a descontar del cubo de IP.
 */
public class RateLimitWebFilter implements WebFilter {

  /** Atributo del intercambio que indica que el filtro ya evaluó la petición. */
  public static final String RATE_LIMIT_CHECKED_ATTRIBUTE = "shoppinglist.rateLimitChecked";

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
    return keyResolver
        .resolve(exchange)
        .flatMap(key -> consume(exchange, key))
        .then(chain.filter(exchange));
  }

  private Mono<Void> consume(ServerWebExchange exchange, String key) {
    Optional<Duration> retryAfter = rateLimiter.consume(key);
    exchange.getAttributes().put(RATE_LIMIT_CHECKED_ATTRIBUTE, Boolean.TRUE);
    if (retryAfter.isPresent()) {
      return Mono.error(new RateLimitExceededException(retryAfter.get()));
    }
    return Mono.empty();
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
