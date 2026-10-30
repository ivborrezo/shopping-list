package dev.ivborrezo.shoppinglist.gateway.support;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Costura de test que provoca un fallo interno del borde.
 *
 * <p>Ante la cabecera {@code X-Test-Trigger: internal-error} lanza una excepción no contemplada,
 * para ejercitar el camino del 500 sin añadir superficie ni rutas en producción.
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 15)
class InternalErrorTriggerFilter implements WebFilter {

  static final String TRIGGER_HEADER = "X-Test-Trigger";

  static final String TRIGGER_VALUE = "internal-error";

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    if (TRIGGER_VALUE.equals(exchange.getRequest().getHeaders().getFirst(TRIGGER_HEADER))) {
      return Mono.error(new IllegalStateException("Sensitive internal failure"));
    }
    return chain.filter(exchange);
  }
}
