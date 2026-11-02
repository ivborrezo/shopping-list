package dev.ivborrezo.shoppinglist.gateway.actuator;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.TestConstructor.AutowireMode;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Test de integración de la exposición de Actuator con el perfil {@code local}.
 *
 * <p>Comprueba que el health está disponible y que el endpoint de rutas del gateway se expone solo
 * en el perfil de depuración, con las dos rutas declaradas.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("local")
@TestConstructor(autowireMode = AutowireMode.ALL)
class ActuatorIntegrationIT {

  private final WebTestClient webTestClient;

  /**
   * Inyecta el cliente HTTP por constructor.
   *
   * @param webTestClient cliente contra el servidor real en puerto aleatorio
   */
  @Autowired
  ActuatorIntegrationIT(WebTestClient webTestClient) {
    this.webTestClient = webTestClient;
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
    webTestClient
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
}
