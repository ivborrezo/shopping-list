package dev.ivborrezo.shoppinglist.gateway.ratelimit;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import dev.ivborrezo.shoppinglist.gateway.support.GatewayTestDestinations;
import dev.ivborrezo.shoppinglist.gateway.support.RateLimitClockTestConfiguration;
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
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.TestConstructor.AutowireMode;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Test de integración del límite por identidad con la resolución real de la clave.
 *
 * <p>Cubre que dos usuarios autenticados que comparten la misma IP mantienen cubos independientes y
 * que el tráfico no autenticado consume el cubo de IP, incluido el que termina en {@code 401}.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@Import(RateLimitClockTestConfiguration.class)
@TestConstructor(autowireMode = AutowireMode.ALL)
class RateLimitIdentityIntegrationIT {

  private static final int LIMIT = 2;

  private static final GatewayTestDestinations DESTINATIONS = GatewayTestDestinations.start();

  private final WebTestClient webTestClient;

  /**
   * Inyecta el cliente HTTP por constructor.
   *
   * @param webTestClient cliente contra el servidor real en puerto aleatorio
   */
  @Autowired
  RateLimitIdentityIntegrationIT(WebTestClient webTestClient) {
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
  void stubDestinations() {
    DESTINATIONS.productService().resetAll();
    DESTINATIONS.listService().resetAll();
    DESTINATIONS.listService().stubFor(get(urlPathEqualTo("/lists")).willReturn(okJson("{}")));
  }

  @Test
  void twoUsersSameIp_haveIndependentBuckets() {
    String firstUser = tokenFor(TestUsers.USER_1);
    String secondUser = tokenFor(TestUsers.USER_2);

    for (int i = 0; i < LIMIT; i++) {
      getLists(firstUser).expectStatus().isOk();
    }

    getLists(firstUser)
        .expectStatus()
        .isEqualTo(HttpStatus.TOO_MANY_REQUESTS)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("RATE_LIMIT_EXCEEDED");

    getLists(secondUser).expectStatus().isOk();

    DESTINATIONS.listService().verify(LIMIT + 1, getRequestedFor(urlPathEqualTo("/lists")));
  }

  @Test
  void unauthenticatedTraffic_consumesIpBucketIncluding401() {
    for (int i = 0; i < LIMIT; i++) {
      webTestClient
          .get()
          .uri("/lists")
          .exchange()
          .expectStatus()
          .isEqualTo(HttpStatus.UNAUTHORIZED);
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
    assertThat(DESTINATIONS.listService().getAllServeEvents()).isEmpty();
  }

  private WebTestClient.ResponseSpec getLists(String token) {
    return webTestClient
        .get()
        .uri("/lists")
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
        .exchange();
  }

  private String tokenFor(UUID userId) {
    return TestJwtFactory.token().subject(userId).build();
  }
}
