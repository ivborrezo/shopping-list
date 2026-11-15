package dev.ivborrezo.shoppinglist.list.service.common.web;

import dev.ivborrezo.shoppinglist.list.service.common.security.SecurityJwtProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Filtro que publica el identificador del usuario autenticado en el MDC de logging.
 *
 * <p>Se ejecuta dentro de la cadena de seguridad, inmediatamente después de {@code
 * BearerTokenAuthenticationFilter}, para que el {@code SecurityContextHolder} ya contenga el JWT
 * validado. El valor se limpia al finalizar la petición mediante el {@code MDC.clear()} de {@link
 * CorrelationIdFilter}.
 */
public class UserIdMdcFilter extends OncePerRequestFilter {

  private static final String MDC_KEY = "userId";

  private final SecurityJwtProperties properties;

  /**
   * Construye el filtro con las propiedades de los claims configurados.
   *
   * @param properties propiedades con el nombre del claim de identificador de usuario
   */
  public UserIdMdcFilter(SecurityJwtProperties properties) {
    this.properties = properties;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    @Nullable UUID userId = currentUserId();
    if (userId != null) {
      MDC.put(MDC_KEY, userId.toString());
    }
    filterChain.doFilter(request, response);
  }

  private @Nullable UUID currentUserId() {
    @Nullable Authentication authentication =
        SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null
        || !authentication.isAuthenticated()
        || !(authentication.getPrincipal() instanceof Jwt jwt)) {
      return null;
    }
    @Nullable String claim = jwt.getClaimAsString(properties.getPrincipalClaim());
    if (claim == null || claim.isBlank()) {
      return null;
    }
    try {
      return UUID.fromString(claim);
    } catch (IllegalArgumentException exception) {
      return null;
    }
  }
}
