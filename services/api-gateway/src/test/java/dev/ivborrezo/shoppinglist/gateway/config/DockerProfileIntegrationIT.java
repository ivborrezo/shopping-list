package dev.ivborrezo.shoppinglist.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ivborrezo.shoppinglist.gateway.security.GatewaySecurityProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.TestConstructor.AutowireMode;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.util.ClassUtils;

/**
 * Test de integración del arranque con el perfil {@code docker}.
 *
 * <p>Cubre el arranque integrado: el contexto levanta sin cliente de Config Server en el classpath
 * y sin resolver el discovery del proveedor OIDC, el registro del cliente enlaza las URIs
 * explícitas del perfil (autorización pública por {@code localhost} y backchannel por el DNS
 * interno) y el endpoint de rutas no queda accesible sin credenciales.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("docker")
@TestConstructor(autowireMode = AutowireMode.ALL)
class DockerProfileIntegrationIT {

  private static final String PUBLIC_REALM = "http://localhost:8083/realms/shopping-list";

  private static final String INTERNAL_REALM = "http://auth-service:8080/realms/shopping-list";

  private final WebTestClient webTestClient;

  private final ReactiveClientRegistrationRepository clientRegistrationRepository;

  private final GatewaySecurityProperties securityProperties;

  private final String resourceServerIssuerUri;

  /**
   * Inyecta el cliente HTTP y los metadatos OIDC enlazados por el perfil {@code docker}.
   *
   * @param webTestClient cliente contra el servidor real en puerto aleatorio
   * @param clientRegistrationRepository registro de clientes OAuth2 enlazado desde configuración
   * @param securityProperties propiedades de identidad y sesión del borde
   * @param resourceServerIssuerUri emisor configurado para el Resource Server JWT
   */
  @Autowired
  DockerProfileIntegrationIT(
      WebTestClient webTestClient,
      ReactiveClientRegistrationRepository clientRegistrationRepository,
      GatewaySecurityProperties securityProperties,
      @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
          String resourceServerIssuerUri) {
    this.webTestClient = webTestClient;
    this.clientRegistrationRepository = clientRegistrationRepository;
    this.securityProperties = securityProperties;
    this.resourceServerIssuerUri = resourceServerIssuerUri;
  }

  @Test
  void dockerProfile_startsWithoutConfigServer() {
    assertThat(
            ClassUtils.isPresent(
                "org.springframework.cloud.config.client.ConfigServicePropertySourceLocator",
                getClass().getClassLoader()))
        .as("Config Server no debe estar en el classpath")
        .isFalse();

    webTestClient
        .get()
        .uri("/actuator/health")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.status")
        .isEqualTo("UP");
  }

  @Test
  void dockerProfile_bindsExplicitOidcUrisWithoutDiscovery() {
    ClientRegistration registration =
        clientRegistrationRepository.findByRegistrationId("keycloak").block();

    assertThat(registration).isNotNull();
    assertThat(registration.getProviderDetails().getAuthorizationUri())
        .isEqualTo(PUBLIC_REALM + "/protocol/openid-connect/auth");
    assertThat(registration.getProviderDetails().getTokenUri())
        .isEqualTo(INTERNAL_REALM + "/protocol/openid-connect/token");
    assertThat(registration.getProviderDetails().getJwkSetUri())
        .isEqualTo(INTERNAL_REALM + "/protocol/openid-connect/certs");
    assertThat(registration.getProviderDetails().getUserInfoEndpoint().getUri())
        .isEqualTo(INTERNAL_REALM + "/protocol/openid-connect/userinfo");
    assertThat(registration.getProviderDetails().getUserInfoEndpoint().getUserNameAttributeName())
        .isEqualTo("sub");
  }

  @Test
  void dockerProfile_usesPublicIssuerForValidation() {
    assertThat(securityProperties.issuerUri()).isEqualTo(PUBLIC_REALM);
    assertThat(resourceServerIssuerUri).isEqualTo(PUBLIC_REALM);
  }

  @Test
  void routesEndpoint_isNotAccessibleWithoutCredentials() {
    webTestClient
        .get()
        .uri("/actuator/gateway/routes")
        .exchange()
        .expectStatus()
        .isEqualTo(HttpStatus.UNAUTHORIZED)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("UNAUTHENTICATED");
  }
}
