package dev.ivborrezo.shoppinglist.gateway.ratelimit;

import dev.ivborrezo.shoppinglist.gateway.security.GatewaySecurityProperties;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Resuelve la clave de rate limiting por usuario autenticado y, en su defecto, por IP de socket.
 *
 * <p>Con una identidad verificada, el cubo es {@code user:<uuid>}, de modo que dos usuarios tras la
 * misma IP no comparten cupo y cambiar de IP no evade el límite. Sin identidad válida el cubo es
 * {@code ip:<dirección>}. No interpreta cabeceras de proxy: sin una política de confianza, {@code
 * X-Forwarded-For} haría el límite evitable. Si el intercambio no expone dirección remota, todas
 * las peticiones anónimas comparten un único cubo.
 */
@Component
public class RemoteAddressKeyResolver implements RateLimitKeyResolver {

  /** Clave compartida por las peticiones sin dirección remota. */
  static final String UNKNOWN_CLIENT_KEY = "unknown-client";

  private static final String USER_PREFIX = "user:";

  private static final String IP_PREFIX = "ip:";

  private final String principalClaim;

  /**
   * Inyecta las propiedades de seguridad para leer el claim de identificador de usuario.
   *
   * @param properties configuración de identidad del borde
   */
  public RemoteAddressKeyResolver(GatewaySecurityProperties properties) {
    this.principalClaim = properties.principalClaim();
  }

  @Override
  public Mono<String> resolve(ServerWebExchange exchange) {
    return ReactiveSecurityContextHolder.getContext()
        .map(securityContext -> securityContext.getAuthentication())
        .flatMap(this::userId)
        .map(userId -> USER_PREFIX + userId)
        .defaultIfEmpty(resolveIpKey(exchange));
  }

  /**
   * Obtiene la clave de IP de la petición, usada también en los rechazos de autenticación.
   *
   * @param exchange intercambio de la petición entrante
   * @return clave {@code ip:<dirección>} del socket
   */
  public String resolveIpKey(ServerWebExchange exchange) {
    InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
    return IP_PREFIX + (remoteAddress != null ? remoteAddress.getHostString() : UNKNOWN_CLIENT_KEY);
  }

  private Mono<String> userId(@Nullable Authentication authentication) {
    if (authentication == null || !authentication.isAuthenticated()) {
      return Mono.empty();
    }
    return Mono.justOrEmpty(principalValue(authentication))
        .filter(value -> !value.isBlank())
        .filter(RemoteAddressKeyResolver::isUuid);
  }

  private @Nullable String principalValue(Authentication authentication) {
    if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
      return jwtAuthentication.getToken().getClaimAsString(principalClaim);
    }
    if (authentication instanceof OAuth2AuthenticationToken oauth2Authentication) {
      Map<String, Object> attributes = oauth2Authentication.getPrincipal().getAttributes();
      Object claim = attributes.get(principalClaim);
      return claim != null ? String.valueOf(claim) : oauth2Authentication.getName();
    }
    return null;
  }

  private static boolean isUuid(String value) {
    try {
      UUID.fromString(value);
      return true;
    } catch (IllegalArgumentException exception) {
      return false;
    }
  }
}
