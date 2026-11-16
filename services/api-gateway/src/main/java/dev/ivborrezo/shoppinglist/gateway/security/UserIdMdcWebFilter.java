package dev.ivborrezo.shoppinglist.gateway.security;

import dev.ivborrezo.shoppinglist.gateway.correlation.CorrelationContext;
import dev.ivborrezo.shoppinglist.gateway.error.ProblemDetailErrorWriter;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Publica el identificador del usuario autenticado en el intercambio y en el contexto reactivo.
 *
 * <p>Se ejecuta tras la fase de autenticación de cada cadena. Extrae el UUID del claim configurado
 * según el tipo de autenticación, leyendo el contexto reactivo del canal {@code Bearer} o el
 * repositorio de contexto de la sesión, lo guarda en los atributos del intercambio para que el log
 * de acceso lo vea al cerrar la petición y lo añade al {@code Reactor Context} para que los filtros
 * posteriores y el {@code MDC} lo reciban. Una identidad sin UUID válido se rechaza con {@code 401
 * UNAUTHENTICATED} a través del escritor único de errores.
 */
public class UserIdMdcWebFilter implements WebFilter {

  private final String principalClaim;

  private final ProblemDetailErrorWriter errorWriter;

  /**
   * Inyecta el claim de identificador de usuario y el escritor de errores del borde.
   *
   * @param principalClaim nombre del claim que transporta el UUID del usuario
   * @param errorWriter escritor único de los errores del borde
   */
  public UserIdMdcWebFilter(String principalClaim, ProblemDetailErrorWriter errorWriter) {
    this.principalClaim = principalClaim;
    this.errorWriter = errorWriter;
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    return authentication(exchange)
        .flatMap(authentication -> Mono.justOrEmpty(principalValue(authentication)))
        .map(Optional::of)
        .defaultIfEmpty(Optional.empty())
        .flatMap(principal -> continueWith(exchange, chain, principal));
  }

  private Mono<Authentication> authentication(ServerWebExchange exchange) {
    return ReactiveSecurityContextHolder.getContext()
        .map(SecurityContext::getAuthentication)
        .filter(Authentication::isAuthenticated);
  }

  private Mono<Void> continueWith(
      ServerWebExchange exchange, WebFilterChain chain, Optional<String> principal) {
    if (principal.isEmpty()) {
      return chain.filter(exchange);
    }
    UUID userId = parseUuid(principal.get());
    if (userId == null) {
      return errorWriter.handle(exchange, invalidSubject());
    }
    exchange.getAttributes().put(CorrelationContext.USER_ID_EXCHANGE_ATTRIBUTE, userId.toString());
    return chain
        .filter(exchange)
        .contextWrite(context -> CorrelationContext.withUserId(context, userId.toString()));
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

  private static @Nullable UUID parseUuid(String value) {
    try {
      return UUID.fromString(value);
    } catch (IllegalArgumentException exception) {
      return null;
    }
  }

  private OAuth2AuthenticationException invalidSubject() {
    return new OAuth2AuthenticationException(
        new OAuth2Error(
            OAuth2ErrorCodes.INVALID_TOKEN,
            "Claim '" + principalClaim + "' is not a valid UUID",
            null));
  }
}
