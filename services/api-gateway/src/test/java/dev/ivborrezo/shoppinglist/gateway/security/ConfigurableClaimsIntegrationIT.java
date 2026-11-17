package dev.ivborrezo.shoppinglist.gateway.security;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import dev.ivborrezo.shoppinglist.gateway.support.GatewayTestDestinations;
import dev.ivborrezo.shoppinglist.testsupport.TestJwtFactory;
import dev.ivborrezo.shoppinglist.testsupport.TestUsers;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.TestConstructor.AutowireMode;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Test de integración de los claims configurables del borde.
 *
 * <p>Configura claims alternativos ({@code user_id} y {@code realm_roles}) para verificar que la
 * validación del sujeto, la trazabilidad y la clave del rate limiting leen el claim configurado y
 * no una ruta fija en código.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@TestConstructor(autowireMode = AutowireMode.ALL)
class ConfigurableClaimsIntegrationIT {

  private static final String PRINCIPAL_CLAIM = "user_id";

  private static final int LIMIT = 2;

  private static final GatewayTestDestinations DESTINATIONS = GatewayTestDestinations.start();

  private final WebTestClient webTestClient;

  private final GatewaySecurityProperties properties;

  /**
   * Inyecta el cliente HTTP y las propiedades de seguridad enlazadas.
   *
   * @param webTestClient cliente contra el servidor real en puerto aleatorio
   * @param properties propiedades de identidad y sesión del contexto
   */
  @Autowired
  ConfigurableClaimsIntegrationIT(
      WebTestClient webTestClient, GatewaySecurityProperties properties) {
    this.webTestClient = webTestClient;
    this.properties = properties;
  }

  @DynamicPropertySource
  static void previousConfiguration(DynamicPropertyRegistry registry) {
    DESTINATIONS.register(registry);
    registry.add("api-gateway.security.principal-claim", () -> PRINCIPAL_CLAIM);
    registry.add("api-gateway.security.roles-claim", () -> "realm_roles");
    registry.add("api-gateway.rate-limit.requests", () -> LIMIT);
    registry.add("api-gateway.rate-limit.window", () -> "60s");
  }

  @AfterAll
  static void stopDestinations() {
    DESTINATIONS.stop();
  }

  @BeforeEach
  void stubDestinations() {
    DESTINATIONS.productService().resetAll();
    DESTINATIONS.listService().resetAll();
    DESTINATIONS.listService().stubFor(get(urlPathEqualTo("/lists")).willReturn(okJson("{}")));
  }

  @Test
  void customClaims_areBoundToSecurityProperties() {
    assertThat(properties.principalClaim()).isEqualTo(PRINCIPAL_CLAIM);
    assertThat(properties.rolesClaim()).isEqualTo("realm_roles");
  }

  @Test
  void tokenWithCustomPrincipalClaim_isAcceptedAndForwarded() {
    String token =
        TestJwtFactory.token()
            .subject("not-a-uuid")
            .claim(PRINCIPAL_CLAIM, TestUsers.ADMIN.toString())
            .build();

    webTestClient
        .get()
        .uri("/lists")
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
        .exchange()
        .expectStatus()
        .isOk();

    assertThat(DESTINATIONS.listService().getAllServeEvents()).hasSize(1);
  }

  @Test
  void tokenWithoutCustomPrincipalClaim_isRejected() {
    String token = TestJwtFactory.token().subject(TestUsers.USER_1).build();

    webTestClient
        .get()
        .uri("/lists")
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
        .exchange()
        .expectStatus()
        .isEqualTo(HttpStatus.UNAUTHORIZED)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("UNAUTHENTICATED");
    assertThat(DESTINATIONS.listService().getAllServeEvents()).isEmpty();
  }

  @Test
  void rateLimit_usesCustomPrincipalClaim() {
    String firstUser = tokenFor(TestUsers.USER_1);
    String secondUser = tokenFor(TestUsers.USER_2);

    for (int i = 0; i < LIMIT; i++) {
      webTestClient
          .get()
          .uri("/lists")
          .header(HttpHeaders.AUTHORIZATION, "Bearer " + firstUser)
          .exchange()
          .expectStatus()
          .isOk();
    }

    webTestClient
        .get()
        .uri("/lists")
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + firstUser)
        .exchange()
        .expectStatus()
        .isEqualTo(HttpStatus.TOO_MANY_REQUESTS)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("RATE_LIMIT_EXCEEDED");

    webTestClient
        .get()
        .uri("/lists")
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + secondUser)
        .exchange()
        .expectStatus()
        .isOk();
  }

  private String tokenFor(UUID userId) {
    return TestJwtFactory.token().subject(userId).claim(PRINCIPAL_CLAIM, userId.toString()).build();
  }
}
