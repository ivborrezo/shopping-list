package dev.ivborrezo.shoppinglist.gateway.ratelimit;

import org.springframework.web.server.ServerWebExchange;

/** Resuelve la clave con la que se agrupa el rate limiting de una petición. */
@FunctionalInterface
public interface RateLimitKeyResolver {

  /**
   * Obtiene la identidad de rate limiting del cliente.
   *
   * @param exchange intercambio de la petición entrante
   * @return clave del cubo que consume la petición
   */
  String resolve(ServerWebExchange exchange);
}
