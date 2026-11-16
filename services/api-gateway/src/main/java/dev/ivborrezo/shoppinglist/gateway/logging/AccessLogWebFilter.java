package dev.ivborrezo.shoppinglist.gateway.logging;

import dev.ivborrezo.shoppinglist.gateway.correlation.CorrelationContext;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
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
 * <p>Incluye método, path, status y duración; el identificador de correlación y el del usuario
 * autenticado llegan al log a través del {@code MDC}. Como el filtro queda fuera de la cadena de
 * seguridad, el usuario lo recupera del atributo del intercambio y lo publica en el {@code MDC}
 * solo durante la escritura de la línea, limpiándolo después. La dirección del cliente solo se
 * emite a nivel debug y nunca se registran cuerpos ni cabeceras de aplicación.
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

    withUserId(
        exchange,
        () ->
            log.info(
                "{} {} {} {} ms",
                request.getMethod(),
                request.getPath().value(),
                status != null ? status.value() : 0,
                elapsedMillis));

    if (log.isDebugEnabled()) {
      log.debug("Client address {}", request.getRemoteAddress());
    }
  }

  private void withUserId(ServerWebExchange exchange, Runnable logLine) {
    String userId = exchange.getAttribute(CorrelationContext.USER_ID_EXCHANGE_ATTRIBUTE);
    boolean mdcApplied = userId != null && MDC.get(CorrelationContext.USER_ID_CONTEXT_KEY) == null;
    if (mdcApplied) {
      MDC.put(CorrelationContext.USER_ID_CONTEXT_KEY, userId);
    }
    try {
      logLine.run();
    } finally {
      if (mdcApplied) {
        MDC.remove(CorrelationContext.USER_ID_CONTEXT_KEY);
      }
    }
  }
}
