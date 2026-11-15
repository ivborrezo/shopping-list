package dev.ivborrezo.shoppinglist.product.service.common.security;

import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Resuelve los parámetros {@link CurrentUserId @CurrentUserId} de tipo {@code UUID} con el
 * identificador del usuario autenticado.
 *
 * <p>Extrae el claim configurado del {@link Jwt} del contexto de seguridad y, si el principal no es
 * un JWT, usa el nombre de la autenticación como respaldo. El valor se convierte a {@code UUID}
 * para que la capa web no manipule claims ni clases de OAuth2.
 */
public class CurrentUserIdArgumentResolver implements HandlerMethodArgumentResolver {

  private final SecurityJwtProperties properties;

  /**
   * Construye el resolver con las propiedades de los claims configurados.
   *
   * @param properties propiedades con el nombre del claim de identificador de usuario
   */
  public CurrentUserIdArgumentResolver(SecurityJwtProperties properties) {
    this.properties = properties;
  }

  @Override
  public boolean supportsParameter(MethodParameter parameter) {
    return parameter.hasParameterAnnotation(CurrentUserId.class)
        && UUID.class.equals(parameter.getParameterType());
  }

  @Override
  public Object resolveArgument(
      MethodParameter parameter,
      @Nullable ModelAndViewContainer mavContainer,
      NativeWebRequest webRequest,
      @Nullable WebDataBinderFactory binderFactory) {
    @Nullable Authentication authentication =
        SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !authentication.isAuthenticated()) {
      throw new AuthenticationCredentialsNotFoundException("No authenticated user");
    }
    if (authentication.getPrincipal() instanceof Jwt jwt) {
      @Nullable String claim = jwt.getClaimAsString(properties.getPrincipalClaim());
      if (claim != null && !claim.isBlank()) {
        return UUID.fromString(claim);
      }
    }
    return UUID.fromString(authentication.getName());
  }
}
