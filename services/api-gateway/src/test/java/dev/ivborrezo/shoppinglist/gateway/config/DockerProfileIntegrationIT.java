package dev.ivborrezo.shoppinglist.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.TestConstructor.AutowireMode;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.util.ClassUtils;

/**
 * Test de integración del arranque con el perfil {@code docker}.
 *
 * <p>Cubre H-06 CA-04 y AD-09: el contexto levanta con configuración local, sin cliente de Config
 * Server en el classpath, y el endpoint de rutas no se expone fuera del perfil {@code local}.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("docker")
@TestConstructor(autowireMode = AutowireMode.ALL)
class DockerProfileIntegrationIT {

  private final WebTestClient webTestClient;

  /**
   * Inyecta el cliente HTTP por constructor.
   *
   * @param webTestClient cliente contra el servidor real en puerto aleatorio
   */
  @Autowired
  DockerProfileIntegrationIT(WebTestClient webTestClient) {
    this.webTestClient = webTestClient;
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
  void routesEndpoint_isNotExposedUnderDockerProfile() {
    webTestClient.get().uri("/actuator/gateway/routes").exchange().expectStatus().isNotFound();
  }
}
