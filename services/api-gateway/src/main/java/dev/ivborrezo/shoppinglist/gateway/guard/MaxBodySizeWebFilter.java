package dev.ivborrezo.shoppinglist.gateway.guard;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Rechaza las peticiones cuyo cuerpo declarado supera el tope configurado.
 *
 * <p>Se ejecuta después de la correlación y del access log, de modo que el 413 conserva el eco del
 * identificador y queda registrado, y antes del rate limiting, de modo que un cuerpo
 * sobredimensionado no consume cupo. La respuesta no pasa por el escritor de errores ni por CORS.
 * Comprueba solo {@code Content-Length} para no bufferizar el cuerpo ni romper el streaming:
 * responde 413 sin reenviar cuando la longitud anunciada supera el límite. Los cuerpos sin longitud
 * declarada no se limitan en este punto.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 15)
public class MaxBodySizeWebFilter implements WebFilter {

  private final long maxBodySizeBytes;

  /**
   * Inyecta el tope de cuerpo configurado.
   *
   * @param maxBodySize tamaño máximo aceptado por {@code Content-Length}
   */
  public MaxBodySizeWebFilter(@Value("${api-gateway.max-body-size:1MB}") DataSize maxBodySize) {
    this.maxBodySizeBytes = maxBodySize.toBytes();
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    if (exchange.getRequest().getHeaders().getContentLength() > maxBodySizeBytes) {
      ServerHttpResponse response = exchange.getResponse();
      response.setStatusCode(HttpStatus.PAYLOAD_TOO_LARGE);
      return response.setComplete();
    }
    return chain.filter(exchange);
  }
}
