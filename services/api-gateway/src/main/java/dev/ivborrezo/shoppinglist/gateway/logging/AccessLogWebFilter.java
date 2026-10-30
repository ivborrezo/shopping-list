package dev.ivborrezo.shoppinglist.gateway.logging;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Registra una línea de acceso por petición al completarse la respuesta.
 *
 * <p>Incluye método, path, status y duración; el identificador de correlación llega al log a través
 * del {@code MDC}. La dirección del cliente solo se emite a nivel debug y nunca se registran
 * cuerpos ni cabeceras de aplicación.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class AccessLogWebFilter implements WebFilter {

  private static final Logger log = LoggerFactory.getLogger(AccessLogWebFilter.class);

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    ServerHttpRequest request = exchange.getRequest();
    long startedAt = System.nanoTime();

    return chain.filter(exchange).doFinally(signal -> logAccess(exchange, request, startedAt));
  }

  private void logAccess(ServerWebExchange exchange, ServerHttpRequest request, long startedAt) {
    HttpStatusCode status = exchange.getResponse().getStatusCode();
    long elapsedMillis = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();

    log.info(
        "{} {} {} {} ms",
        request.getMethod(),
        request.getPath().value(),
        status != null ? status.value() : 0,
        elapsedMillis);

    if (log.isDebugEnabled()) {
      log.debug("Client address {}", request.getRemoteAddress());
    }
  }
}
