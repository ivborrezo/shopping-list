package dev.ivborrezo.shoppinglist.gateway.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuración de identidad y sesión del borde.
 *
 * <p>El emisor esperado, los nombres de los claims y las cotas del almacén de sesiones se
 * externalizan para validar tokens de cualquier proveedor OIDC estándar sin cambiar el código.
 *
 * @param issuerUri emisor esperado de los tokens y del {@code id_token}
 * @param principalClaim claim que transporta el identificador de usuario
 * @param rolesClaim claim que transporta los roles
 * @param sessionTtl tiempo máximo de inactividad de una sesión
 * @param maxSessions número máximo de sesiones concurrentes en memoria
 */
@ConfigurationProperties("api-gateway.security")
public record GatewaySecurityProperties(
    String issuerUri,
    @DefaultValue("sub") String principalClaim,
    @DefaultValue("roles") String rolesClaim,
    @DefaultValue("30m") Duration sessionTtl,
    @DefaultValue("10000") int maxSessions) {

  /**
   * Valida que los claims y las cotas de sesión tengan valores utilizables.
   *
   * @throws IllegalArgumentException si algún claim está en blanco o una cota no es positiva
   */
  public GatewaySecurityProperties {
    if (issuerUri == null || issuerUri.isBlank()) {
      throw new IllegalArgumentException("api-gateway.security.issuer-uri must be configured");
    }
    if (principalClaim == null || principalClaim.isBlank()) {
      throw new IllegalArgumentException("api-gateway.security.principal-claim must not be blank");
    }
    if (rolesClaim == null || rolesClaim.isBlank()) {
      throw new IllegalArgumentException("api-gateway.security.roles-claim must not be blank");
    }
    if (sessionTtl == null || sessionTtl.isZero() || sessionTtl.isNegative()) {
      throw new IllegalArgumentException("api-gateway.security.session-ttl must be positive");
    }
    if (maxSessions < 1) {
      throw new IllegalArgumentException("api-gateway.security.max-sessions must be positive");
    }
  }
}
