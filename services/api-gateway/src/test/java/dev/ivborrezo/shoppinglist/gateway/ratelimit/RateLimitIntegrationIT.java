package dev.ivborrezo.shoppinglist.gateway.ratelimit;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import dev.ivborrezo.shoppinglist.gateway.correlation.CorrelationContext;
import dev.ivborrezo.shoppinglist.gateway.support.GatewayTestDestinations;
import dev.ivborrezo.shoppinglist.gateway.support.RateLimitTestConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.TestConstructor.AutowireMode;
import org.springframework.test.web.reactive.server.ExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Test de integración del rate limiting del borde.
 *
 * <p>Cubre H-03 con la clave de test: las peticiones dentro del límite llegan al destino, el
 * rechazo responde 429 con {@code ProblemDetail}, {@code Retry-After}, eco de correlación y CORS,
 * cada clave mantiene su cubo, las rutas no expuestas también se limitan, ni el preflight ni el
 * actuator consumen cupo y un {@code OPTIONS} sin cabeceras de preflight sí lo consume.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@Import(RateLimitTestConfiguration.class)
@TestConstructor(autowireMode = AutowireMode.ALL)
class RateLimitIntegrationIT {

  private static final int LIMIT = 3;

  private static final String TEST_CLIENT_HEADER = RateLimitTestConfiguration.TEST_CLIENT_HEADER;

  private static final String ALLOWED_ORIGIN = "http://localhost:5173";

  private static final String CORRELATION_ID = "3f2504e0-4f89-41d3-9a0c-0305e82c3301";

  private static final GatewayTestDestinations DESTINATIONS = GatewayTestDestinations.start();

  private final WebTestClient webTestClient;

  /**
   * Inyecta el cliente HTTP por constructor.
   *
   * @param webTestClient cliente contra el servidor real en puerto aleatorio
   */
  @Autowired
  RateLimitIntegrationIT(WebTestClient webTestClient) {
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
  void requestsWithinLimit_reachDestination() {
    consume("within-limit");

    DESTINATIONS.listService().verify(LIMIT, getRequestedFor(urlPathEqualTo("/lists")));
  }

  @Test
  void exceededLimit_returns429_withProblemDetailRetryAfterCorrelationAndCors() {
    consume("exhausted");

    ExchangeResult result =
        webTestClient
            .get()
            .uri("/lists")
            .header(TEST_CLIENT_HEADER, "exhausted")
            .header(CorrelationContext.HEADER_NAME, CORRELATION_ID)
            .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
            .exchange()
            .expectStatus()
            .isEqualTo(429)
            .returnResult();

    assertThat(result.getResponseHeaders().getContentType())
        .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    String body = new String(result.getResponseBodyContent());
    assertThat(body)
        .contains("\"title\":\"Too many requests\"")
        .contains("\"status\":429")
        .contains("\"instance\":\"/lists\"")
        .contains("\"code\":\"RATE_LIMIT_EXCEEDED\"");
    assertThat(result.getResponseHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("20");
    assertThat(result.getResponseHeaders().get(CorrelationContext.HEADER_NAME))
        .containsExactly(CORRELATION_ID);
    assertThat(result.getResponseHeaders().getAccessControlAllowOrigin()).isEqualTo(ALLOWED_ORIGIN);
    assertThat(result.getResponseHeaders().getAccessControlExposeHeaders())
        .containsExactly(CorrelationContext.HEADER_NAME);
    DESTINATIONS.listService().verify(LIMIT, getRequestedFor(urlPathEqualTo("/lists")));
  }

  @Test
  void exhaustedKey_doesNotAffectOtherKeys() {
    consume("first-client");

    webTestClient
        .get()
        .uri("/lists")
        .header(TEST_CLIENT_HEADER, "first-client")
        .exchange()
        .expectStatus()
        .isEqualTo(429);
    webTestClient
        .get()
        .uri("/lists")
        .header(TEST_CLIENT_HEADER, "second-client")
        .exchange()
        .expectStatus()
        .isOk();
  }

  @Test
  void unexposedRoutes_areLimitedToo() {
    String client = "unrouted-client";
    for (int i = 0; i < LIMIT; i++) {
      webTestClient
          .get()
          .uri("/dataviz")
          .header(TEST_CLIENT_HEADER, client)
          .exchange()
          .expectStatus()
          .isNotFound();
    }

    webTestClient
        .get()
        .uri("/dataviz")
        .header(TEST_CLIENT_HEADER, client)
        .exchange()
        .expectStatus()
        .isEqualTo(429)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("RATE_LIMIT_EXCEEDED");
    assertThat(DESTINATIONS.productService().getAllServeEvents()).isEmpty();
    assertThat(DESTINATIONS.listService().getAllServeEvents()).isEmpty();
  }

  @Test
  void preflight_doesNotConsumeTokens() {
    String client = "preflight-client";
    for (int i = 0; i < LIMIT + 2; i++) {
      webTestClient
          .options()
          .uri("/lists")
          .header(TEST_CLIENT_HEADER, client)
          .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
          .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
          .exchange()
          .expectStatus()
          .isOk();
    }

    webTestClient
        .get()
        .uri("/lists")
        .header(TEST_CLIENT_HEADER, client)
        .exchange()
        .expectStatus()
        .isOk();
  }

  @Test
  void plainOptions_consumeTokens() {
    String client = "plain-options-client";
    for (int i = 0; i < LIMIT; i++) {
      ExchangeResult result =
          webTestClient
              .options()
              .uri("/dataviz")
              .header(TEST_CLIENT_HEADER, client)
              .exchange()
              .returnResult();

      assertThat(result.getStatus().value()).isNotEqualTo(429);
    }

    webTestClient
        .get()
        .uri("/dataviz")
        .header(TEST_CLIENT_HEADER, client)
        .exchange()
        .expectStatus()
        .isEqualTo(429)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("RATE_LIMIT_EXCEEDED");
  }

  @Test
  void actuator_doesNotConsumeTokens() {
    String client = "actuator-client";
    for (int i = 0; i < LIMIT + 2; i++) {
      webTestClient
          .get()
          .uri("/actuator/health")
          .header(TEST_CLIENT_HEADER, client)
          .exchange()
          .expectStatus()
          .isOk();
    }

    webTestClient
        .get()
        .uri("/lists")
        .header(TEST_CLIENT_HEADER, client)
        .exchange()
        .expectStatus()
        .isOk();
  }

  private void consume(String client) {
    for (int i = 0; i < LIMIT; i++) {
      webTestClient
          .get()
          .uri("/lists")
          .header(TEST_CLIENT_HEADER, client)
          .exchange()
          .expectStatus()
          .isOk();
    }
  }
}
