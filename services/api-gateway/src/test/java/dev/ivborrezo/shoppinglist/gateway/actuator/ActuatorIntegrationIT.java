package dev.ivborrezo.shoppinglist.gateway.actuator;

import dev.ivborrezo.shoppinglist.gateway.support.GatewayTestDestinations;
import dev.ivborrezo.shoppinglist.testsupport.TestJwtFactory;
import dev.ivborrezo.shoppinglist.testsupport.TestUsers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.TestConstructor.AutowireMode;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Test de integración de la exposición de Actuator con el perfil {@code local}.
 *
 * <p>Comprueba que el health está disponible sin credenciales y que el endpoint de rutas del
 * gateway se expone solo en el perfil de depuración, con las dos rutas declaradas y exigiendo una
 * identidad verificada.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("local")
@TestConstructor(autowireMode = AutowireMode.ALL)
class ActuatorIntegrationIT {

  private static final String BEARER_TOKEN =
      TestJwtFactory.token().subject(TestUsers.ADMIN).roles("ADMIN").build();

  private static final GatewayTestDestinations DESTINATIONS = GatewayTestDestinations.start();

  private final WebTestClient webTestClient;

  private final WebTestClient authenticatedClient;

  /**
   * Inyecta el cliente HTTP por constructor.
   *
   * @param webTestClient cliente contra el servidor real en puerto aleatorio
   */
  @Autowired
  ActuatorIntegrationIT(WebTestClient webTestClient) {
    this.webTestClient = webTestClient;
    this.authenticatedClient =
        webTestClient
            .mutate()
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + BEARER_TOKEN)
            .build();
  }

  @DynamicPropertySource
  static void destinations(DynamicPropertyRegistry registry) {
    DESTINATIONS.register(registry);
  }

  @AfterAll
  static void stopDestinations() {
    DESTINATIONS.stop();
  }

  @Test
  void health_isExposedUnderLocalProfile() {
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
  void routesEndpoint_isExposedUnderLocalProfile() {
    authenticatedClient
        .get()
        .uri("/actuator/gateway/routes")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.length()")
        .isEqualTo(2)
        .jsonPath("$[0].route_id")
        .isEqualTo("product-service")
        .jsonPath("$[1].route_id")
        .isEqualTo("list-service");
  }

  @Test
  void routesEndpoint_withoutCredentials_returns401() {
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
