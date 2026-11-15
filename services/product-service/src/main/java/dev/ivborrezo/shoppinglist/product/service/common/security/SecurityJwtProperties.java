package dev.ivborrezo.shoppinglist.product.service.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propiedades de configuración del JWT consumido por el servicio.
 *
 * <p>Los nombres de los claims que transportan el identificador de usuario y los roles se
 * externalizan para poder validar tokens de cualquier proveedor OIDC estándar sin cambiar el
 * código.
 */
@ConfigurationProperties(prefix = "security.jwt")
public class SecurityJwtProperties {

  private String principalClaim = "sub";

  private String rolesClaim = "roles";

  /**
   * Devuelve el claim que transporta el identificador de usuario.
   *
   * @return nombre del claim de identificador de usuario
   */
  public String getPrincipalClaim() {
    return principalClaim;
  }

  /** Fija el claim que transporta el identificador de usuario. */
  public void setPrincipalClaim(String principalClaim) {
    this.principalClaim = principalClaim;
  }

  /**
   * Devuelve el claim que transporta los roles del usuario.
   *
   * @return nombre del claim de roles
   */
  public String getRolesClaim() {
    return rolesClaim;
  }

  /** Fija el claim que transporta los roles del usuario. */
  public void setRolesClaim(String rolesClaim) {
    this.rolesClaim = rolesClaim;
  }
}
