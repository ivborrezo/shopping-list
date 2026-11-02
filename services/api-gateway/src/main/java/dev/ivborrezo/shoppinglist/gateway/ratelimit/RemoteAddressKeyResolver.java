package dev.ivborrezo.shoppinglist.gateway.ratelimit;

import java.net.InetSocketAddress;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

/**
 * Resuelve la clave de rate limiting con la dirección remota del socket.
 *
 * <p>No interpreta cabeceras de proxy: sin una política de confianza, {@code X-Forwarded-For} haría
 * el límite evitable. Si el intercambio no expone dirección remota, todas las peticiones comparten
 * un único cubo.
 */
@Component
public class RemoteAddressKeyResolver implements RateLimitKeyResolver {

  /** Clave compartida por las peticiones sin dirección remota. */
  static final String UNKNOWN_CLIENT_KEY = "unknown-client";

  @Override
  public String resolve(ServerWebExchange exchange) {
    InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
    return remoteAddress != null ? remoteAddress.getHostString() : UNKNOWN_CLIENT_KEY;
  }
}
