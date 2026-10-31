package dev.ivborrezo.shoppinglist.gateway.error;

import dev.ivborrezo.shoppinglist.gateway.correlation.CorrelationContext;
import dev.ivborrezo.shoppinglist.gateway.cors.BorderCorsSupport;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.webflux.error.ErrorWebExceptionHandler;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

/**
 * Escritor único de los errores que genera el propio borde.
 *
 * <p>Traduce la excepción con {@link BorderErrorMapper}, compone un {@code ProblemDetail} con el
 * status, título, detalle, instancia y código del catálogo, y lo serializa como {@code
 * application/problem+json}. El diagnóstico técnico se registra en el log con el {@code
 * correlationId} de la petición; el cuerpo nunca expone el mensaje original ni la topología
 * interna. La respuesta lleva las cabeceras CORS del origen permitido que aplica {@link
 * BorderCorsSupport}.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ProblemDetailErrorWriter implements ErrorWebExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(ProblemDetailErrorWriter.class);

  private final BorderErrorMapper errorMapper;

  private final ObjectMapper objectMapper;

  private final BorderCorsSupport corsSupport;

  /**
   * Inyecta el mapeo de errores, el serializador del cuerpo y la política CORS del borde.
   *
   * @param errorMapper traductor de excepciones a códigos del borde
   * @param objectMapper serializador de Jackson configurado por la aplicación
   * @param corsSupport aplicación de la política CORS a las respuestas de error
   */
  public ProblemDetailErrorWriter(
      BorderErrorMapper errorMapper, ObjectMapper objectMapper, BorderCorsSupport corsSupport) {
    this.errorMapper = errorMapper;
    this.objectMapper = objectMapper;
    this.corsSupport = corsSupport;
  }

  @Override
  public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
    ServerHttpResponse response = exchange.getResponse();
    if (response.isCommitted()) {
      return Mono.error(ex);
    }

    ErrorCode code = errorMapper.map(ex);
    logDiagnostic(code, exchange, ex);
    byte[] body = objectMapper.writeValueAsBytes(problemDetail(code, exchange.getRequest()));

    response.setStatusCode(HttpStatus.valueOf(code.getHttpStatus()));
    response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
    response.getHeaders().setContentLength(body.length);
    corsSupport.apply(exchange);
    return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
  }

  private ProblemDetail problemDetail(ErrorCode code, ServerHttpRequest request) {
    ProblemDetail problemDetail =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.valueOf(code.getHttpStatus()), code.getDetail());
    problemDetail.setType(URI.create("about:blank"));
    problemDetail.setTitle(code.getTitle());
    problemDetail.setInstance(URI.create(request.getPath().value()));
    problemDetail.setProperty("code", code.name());
    return problemDetail;
  }

  private void logDiagnostic(ErrorCode code, ServerWebExchange exchange, Throwable ex) {
    String correlationId =
        exchange.getRequest().getHeaders().getFirst(CorrelationContext.HEADER_NAME);
    boolean mdcAvailable = correlationId != null;
    if (mdcAvailable) {
      MDC.put(CorrelationContext.CONTEXT_KEY, correlationId);
    }
    try {
      if (code == ErrorCode.ROUTE_NOT_FOUND) {
        log.debug("No route matched {}", exchange.getRequest().getPath());
      } else if (code == ErrorCode.SERVICE_UNAVAILABLE) {
        log.warn("Upstream service unavailable", ex);
      } else {
        log.error("Unhandled gateway error", ex);
      }
    } finally {
      if (mdcAvailable) {
        MDC.remove(CorrelationContext.CONTEXT_KEY);
      }
    }
  }
}
