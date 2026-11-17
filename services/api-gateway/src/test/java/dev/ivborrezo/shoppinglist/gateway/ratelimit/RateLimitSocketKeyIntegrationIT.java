package dev.ivborrezo.shoppinglist.gateway.ratelimit;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import dev.ivborrezo.shoppinglist.gateway.support.GatewayTestDestinations;
import dev.ivborrezo.shoppinglist.gateway.support.MutableClock;
import dev.ivborrezo.shoppinglist.gateway.support.RateLimitClockTestConfiguration;
import java.time.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.TestConstructor.AutowireMode;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Test de integración de la clave de producción del rate limiting.
 *
 * <p>Usa la dirección del socket y agrupa en un solo cubo todo el tráfico de la misma IP, sin que
 * {@code X-Forwarded-For} altere la identidad. Verifica que las rutas públicas de inicio de sesión
 * y los rechazos de credenciales inválidas consumen el cubo de IP, incluido el {@code 429} cuando
 * se agota.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@Import(RateLimitClockTestConfiguration.class)
@TestConstructor(autowireMode = AutowireMode.ALL)
class RateLimitSocketKeyIntegrationIT {

  private static final int LIMIT = 2;

  private static final String FORWARDED_FOR = "X-Forwarded-For";

  private static final GatewayTestDestinations DESTINATIONS = GatewayTestDestinations.start();

  private final WebTestClient webTestClient;

  private final MutableClock clock;

  /**
   * Inyecta el cliente HTTP y el reloj avanzable de test.
   *
   * @param webTestClient cliente contra el servidor real en puerto aleatorio
   * @param clock reloj con el que se recargan los cubos
   */
  @Autowired
  RateLimitSocketKeyIntegrationIT(WebTestClient webTestClient, MutableClock clock) {
    this.webTestClient = webTestClient;
    this.clock = clock;
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
  void resetBuckets() {
    clock.advance(Duration.ofMinutes(2));
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
          .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    webTestClient
        .get()
        .uri("/lists")
        .header(FORWARDED_FOR, "198.51.100.200")
        .exchange()
        .expectStatus()
        .isEqualTo(HttpStatus.TOO_MANY_REQUESTS)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("RATE_LIMIT_EXCEEDED");
    assertThat(DESTINATIONS.listService().getAllServeEvents()).isEmpty();
  }

  @Test
  void oauthRoutes_consumeIpQuota() {
    for (int i = 0; i < LIMIT; i++) {
      webTestClient.get().uri("/oauth2/authorization/keycloak").exchange().expectStatus().isFound();
    }

    webTestClient
        .get()
        .uri("/lists")
        .exchange()
        .expectStatus()
        .isEqualTo(HttpStatus.TOO_MANY_REQUESTS)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("RATE_LIMIT_EXCEEDED");
  }

  @Test
  void malformedBearerToken_consumesIpQuota() {
    for (int i = 0; i < LIMIT; i++) {
      webTestClient
          .get()
          .uri("/lists")
          .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-valid-jwt")
          .exchange()
          .expectStatus()
          .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    webTestClient
        .get()
        .uri("/lists")
        .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-valid-jwt")
        .exchange()
        .expectStatus()
        .isEqualTo(HttpStatus.TOO_MANY_REQUESTS)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("RATE_LIMIT_EXCEEDED");
    DESTINATIONS.listService().verify(0, getRequestedFor(urlPathEqualTo("/lists")));
  }
}
