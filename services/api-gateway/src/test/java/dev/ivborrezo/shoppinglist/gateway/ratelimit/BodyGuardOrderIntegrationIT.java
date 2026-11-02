package dev.ivborrezo.shoppinglist.gateway.ratelimit;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
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
 * Test de integración del orden entre el guard de cuerpo y el rate limiting.
 *
 * <p>Fija que el guard rechaza el cuerpo sobredimensionado después de la correlación y antes de
 * consumir cupo: el 413 no gasta token, el tope real es el configurado y las peticiones que caben
 * en el cuerpo consumen el cubo hasta el 429.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@Import(RateLimitTestConfiguration.class)
@TestConstructor(autowireMode = AutowireMode.ALL)
class BodyGuardOrderIntegrationIT {

  private static final int LIMIT = 4;

  private static final String TEST_CLIENT_HEADER = RateLimitTestConfiguration.TEST_CLIENT_HEADER;

  private static final String CLIENT = "body-guard-client";

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
  BodyGuardOrderIntegrationIT(WebTestClient webTestClient) {
    this.webTestClient = webTestClient;
  }

  @DynamicPropertySource
  static void bodyGuardProperties(DynamicPropertyRegistry registry) {
    DESTINATIONS.register(registry);
    registry.add("api-gateway.rate-limit.requests", () -> LIMIT);
    registry.add("api-gateway.rate-limit.window", () -> "60s");
    registry.add("api-gateway.max-body-size", () -> "64KB");
  }

  @AfterAll
  static void stopDestinations() {
    DESTINATIONS.stop();
  }

  @BeforeEach
  void stubDestination() {
    DESTINATIONS.productService().resetAll();
    DESTINATIONS.listService().resetAll();
    DESTINATIONS
        .listService()
        .stubFor(
            post(urlPathEqualTo("/lists"))
                .willReturn(
                    aResponse()
                        .withStatus(201)
                        .withHeader("Location", "/lists/1b4e28ba-2fa1-11d2-883f-0016d3cca427")
                        .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .withBody("{}")));
    DESTINATIONS.listService().stubFor(get(urlPathEqualTo("/lists")).willReturn(okJson("{}")));
  }

  @Test
  void oversizedBody_isRejectedWithoutConsumingTokens() {
    ExchangeResult rejected =
        webTestClient
            .post()
            .uri("/lists")
            .header(TEST_CLIENT_HEADER, CLIENT)
            .header(CorrelationContext.HEADER_NAME, CORRELATION_ID)
            .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .bodyValue(body(100 * 1024))
            .exchange()
            .expectStatus()
            .isEqualTo(413)
            .returnResult();

    assertThat(rejected.getResponseHeaders().getContentType())
        .isNotEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    assertThat(rejected.getResponseHeaders().get(CorrelationContext.HEADER_NAME))
        .containsExactly(CORRELATION_ID);
    assertThat(rejected.getResponseHeaders().getAccessControlAllowOrigin()).isNull();
    DESTINATIONS.listService().verify(0, postRequestedFor(urlPathEqualTo("/lists")));

    webTestClient
        .post()
        .uri("/lists")
        .header(TEST_CLIENT_HEADER, CLIENT)
        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        .bodyValue(body(50 * 1024))
        .exchange()
        .expectStatus()
        .isCreated();

    DESTINATIONS.listService().verify(1, postRequestedFor(urlPathEqualTo("/lists")));

    for (int i = 0; i < LIMIT - 1; i++) {
      webTestClient
          .get()
          .uri("/lists")
          .header(TEST_CLIENT_HEADER, CLIENT)
          .exchange()
          .expectStatus()
          .isOk();
    }

    DESTINATIONS.listService().verify(3, getRequestedFor(urlPathEqualTo("/lists")));

    webTestClient
        .get()
        .uri("/lists")
        .header(TEST_CLIENT_HEADER, CLIENT)
        .exchange()
        .expectStatus()
        .isEqualTo(429)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("RATE_LIMIT_EXCEEDED");
  }

  private static String body(int size) {
    return "{\"name\":\"" + "a".repeat(size) + "\"}";
  }
}
