package dev.ivborrezo.shoppinglist.gateway.ratelimit;

import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/** Resuelve la clave con la que se agrupa el rate limiting de una petición. */
@FunctionalInterface
public interface RateLimitKeyResolver {

  /**
   * Obtiene la identidad de rate limiting del cliente.
   *
   * <p>La resolución es reactiva porque la identidad del usuario solo está disponible en el
   * contexto de seguridad una vez autenticada la petición.
   *
   * @param exchange intercambio de la petición entrante
   * @return clave del cubo que consume la petición
   */
  Mono<String> resolve(ServerWebExchange exchange);
}
