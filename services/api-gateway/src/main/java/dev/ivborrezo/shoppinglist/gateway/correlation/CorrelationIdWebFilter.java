package dev.ivborrezo.shoppinglist.gateway.correlation;

import java.util.List;
import java.util.UUID;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Normaliza el identificador de correlación de toda petición que entra por el borde.
 *
 * <p>Reutiliza el valor recibido solo si es un UUID canónico y llega como valor único; en cualquier
 * otro caso genera un UUID v4. El valor definitivo viaja en la petición reenviada, en el contexto
 * reactivo y en el eco de la respuesta, siempre con un único valor.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdWebFilter implements WebFilter {

  /** Crea el filtro y registra el puente del identificador hacia {@code MDC}. */
  public CorrelationIdWebFilter() {
    CorrelationContext.registerMdcAccessor();
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    ServerHttpRequest request = exchange.getRequest();
    String correlationId = resolveCorrelationId(request);
    ServerWebExchange mutatedExchange =
        exchange
            .mutate()
            .request(
                request
                    .mutate()
                    .headers(headers -> headers.set(CorrelationContext.HEADER_NAME, correlationId))
                    .build())
            .build();

    mutatedExchange
        .getResponse()
        .beforeCommit(
            () -> {
              mutatedExchange
                  .getResponse()
                  .getHeaders()
                  .set(CorrelationContext.HEADER_NAME, correlationId);
              return Mono.empty();
            });

    return chain
        .filter(mutatedExchange)
        .contextWrite(context -> CorrelationContext.withCorrelationId(context, correlationId));
  }

  private String resolveCorrelationId(ServerHttpRequest request) {
    List<String> values = request.getHeaders().get(CorrelationContext.HEADER_NAME);
    if (values != null && values.size() == 1 && isCanonicalUuid(values.getFirst())) {
      return values.getFirst();
    }
    return UUID.randomUUID().toString();
  }

  private boolean isCanonicalUuid(String value) {
    if (value.length() != 36
        || value.charAt(8) != '-'
        || value.charAt(13) != '-'
        || value.charAt(18) != '-'
        || value.charAt(23) != '-') {
      return false;
    }
    try {
      UUID.fromString(value);
      return true;
    } catch (IllegalArgumentException ex) {
      return false;
    }
  }
}
