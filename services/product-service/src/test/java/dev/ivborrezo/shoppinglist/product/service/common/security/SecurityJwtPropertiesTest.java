package dev.ivborrezo.shoppinglist.product.service.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Test unitario del binding de {@link SecurityJwtProperties}.
 *
 * <p>Comprueba los nombres de claim por defecto y su sobrescritura mediante propiedades de
 * configuración.
 */
class SecurityJwtPropertiesTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner().withUserConfiguration(PropertiesConfig.class);

  @Test
  void bindsDefaultClaims() {
    contextRunner.run(
        context -> {
          SecurityJwtProperties properties = context.getBean(SecurityJwtProperties.class);
          assertThat(properties.getPrincipalClaim()).isEqualTo("sub");
          assertThat(properties.getRolesClaim()).isEqualTo("roles");
        });
  }

  @Test
  void bindsConfiguredClaims() {
    contextRunner
        .withPropertyValues(
            "security.jwt.principal-claim=user_id", "security.jwt.roles-claim=realm_roles")
        .run(
            context -> {
              SecurityJwtProperties properties = context.getBean(SecurityJwtProperties.class);
              assertThat(properties.getPrincipalClaim()).isEqualTo("user_id");
              assertThat(properties.getRolesClaim()).isEqualTo("realm_roles");
            });
  }

  /** Configuración mínima que habilita el binding de las propiedades del JWT. */
  @TestConfiguration
  @EnableConfigurationProperties(SecurityJwtProperties.class)
  static class PropertiesConfig {}
}
