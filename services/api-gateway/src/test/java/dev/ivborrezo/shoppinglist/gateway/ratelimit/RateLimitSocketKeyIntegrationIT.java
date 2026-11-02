package dev.ivborrezo.shoppinglist.gateway.ratelimit;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

import dev.ivborrezo.shoppinglist.gateway.support.GatewayTestDestinations;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.TestConstructor.AutowireMode;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Test de integración de la clave de producción del rate limiting.
 *
 * <p>Usa la dirección del socket y agrupa en un solo cubo todo el tráfico de la misma IP, sin que
 * {@code X-Forwarded-For} altere la identidad. El agotamiento es el escenario que lo demuestra.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@TestConstructor(autowireMode = AutowireMode.ALL)
class RateLimitSocketKeyIntegrationIT {

  private static final int LIMIT = 3;

  private static final String FORWARDED_FOR = "X-Forwarded-For";

  private static final GatewayTestDestinations DESTINATIONS = GatewayTestDestinations.start();

  private final WebTestClient webTestClient;

  /**
   * Inyecta el cliente HTTP por constructor.
   *
   * @param webTestClient cliente contra el servidor real en puerto aleatorio
   */
  @Autowired
  RateLimitSocketKeyIntegrationIT(WebTestClient webTestClient) {
    this.webTestClient = webTestClient;
  }

  @DynamicPropertySource
  static void rateLimitProperties(DynamicPropertyRegistry registry) {
    DESTINATIONS.register(registry);
    registry.add("api-gateway.rate-limit.requests", () -> LIMIT);
    registry.add("api-gateway.rate-limit.window", () -> "60s");
  }

  @AfterAll
  static void stopDestinations() {
    DESTINATIONS.stop();
  }

  @BeforeEach
  void stubDestination() {
    DESTINATIONS.productService().resetAll();
    DESTINATIONS.listService().resetAll();
    DESTINATIONS.listService().stubFor(get(urlPathEqualTo("/lists")).willReturn(okJson("{}")));
  }

  @Test
  void socketKey_aggregatesTraffic_andIgnoresForwardedFor() {
    for (int i = 0; i < LIMIT; i++) {
      webTestClient
          .get()
          .uri("/lists")
          .header(FORWARDED_FOR, "198.51.100." + i)
          .exchange()
          .expectStatus()
          .isOk();
    }

    webTestClient
        .get()
        .uri("/lists")
        .header(FORWARDED_FOR, "198.51.100.200")
        .exchange()
        .expectStatus()
        .isEqualTo(429)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("RATE_LIMIT_EXCEEDED");

    DESTINATIONS.listService().verify(LIMIT, getRequestedFor(urlPathEqualTo("/lists")));
  }
}
