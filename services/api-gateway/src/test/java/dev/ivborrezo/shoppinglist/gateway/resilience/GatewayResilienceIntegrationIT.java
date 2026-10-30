package dev.ivborrezo.shoppinglist.gateway.resilience;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import dev.ivborrezo.shoppinglist.gateway.support.GatewayTestDestinations;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.TestConstructor.AutowireMode;
import org.springframework.test.web.reactive.server.ExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Test de integración de la resiliencia del transporte del borde.
 *
 * <p>Cubre E3-AD-08: timeout de respuesta y conexión rechazada producen 503 sin reintentos; las
 * cabeceras hop-by-hop no cruzan; los topes de cabecera y cuerpo rechazan sin reenviar, y {@code
 * Location} relativo se reenvía intacto.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@TestConstructor(autowireMode = AutowireMode.ALL)
class GatewayResilienceIntegrationIT {

  private static final String HEADER = "X-Correlation-Id";

  private static final String LARGE_HEADER_VALUE = "a".repeat(10 * 1024);

  private static final int CLOSED_PORT = findClosedPort();

  private static final GatewayTestDestinations DESTINATIONS = GatewayTestDestinations.start();

  private final WebTestClient webTestClient;

  /**
   * Inyecta el cliente HTTP por constructor.
   *
   * @param webTestClient cliente contra el servidor real en puerto aleatorio
   */
  @Autowired
  GatewayResilienceIntegrationIT(WebTestClient webTestClient) {
    this.webTestClient = webTestClient;
  }

  @DynamicPropertySource
  static void destinations(DynamicPropertyRegistry registry) {
    DESTINATIONS.register(registry);
    registry.add("product-service.client.base-url", () -> "http://127.0.0.1:" + CLOSED_PORT);
    registry.add("spring.cloud.gateway.server.webflux.httpclient.response-timeout", () -> "300ms");
  }

  @AfterAll
  static void stopDestinations() {
    DESTINATIONS.stop();
  }

  @BeforeEach
  void resetStubs() {
    DESTINATIONS.listService().resetAll();
  }

  @Test
  void responseTimeout_returns503() {
    DESTINATIONS
        .listService()
        .stubFor(
            get(urlPathEqualTo("/lists"))
                .willReturn(aResponse().withFixedDelay(1_200).withBody("{}")));

    ExchangeResult result =
        webTestClient.get().uri("/lists").exchange().expectStatus().isEqualTo(503).returnResult();

    assertThat(new String(result.getResponseBodyContent()))
        .contains("\"code\":\"SERVICE_UNAVAILABLE\"");
    DESTINATIONS.listService().verify(1, getRequestedFor(urlPathEqualTo("/lists")));
  }

  @Test
  void connectionRefused_returns503() {
    ExchangeResult result =
        webTestClient
            .get()
            .uri("/categories")
            .exchange()
            .expectStatus()
            .isEqualTo(503)
            .returnResult();

    String body = new String(result.getResponseBodyContent());
    assertThat(body)
        .contains("\"code\":\"SERVICE_UNAVAILABLE\"")
        .doesNotContain("127.0.0.1")
        .doesNotContain(String.valueOf(CLOSED_PORT));
  }

  @Test
  void postIsSentOnce_whenDestinationTimesOut() {
    DESTINATIONS
        .listService()
        .stubFor(
            post(urlPathEqualTo("/lists"))
                .willReturn(aResponse().withFixedDelay(1_200).withBody("{}")));

    webTestClient
        .post()
        .uri("/lists")
        .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
        .bodyValue("{}")
        .exchange()
        .expectStatus()
        .isEqualTo(503);

    DESTINATIONS.listService().verify(1, postRequestedFor(urlPathEqualTo("/lists")));
  }

  @Test
  void hopByHopHeaders_areNotForwarded() {
    DESTINATIONS.listService().stubFor(get(urlPathEqualTo("/lists")).willReturn(okJson("{}")));

    webTestClient
        .get()
        .uri("/lists")
        .header("Connection", "X-Hop")
        .header("X-Hop", "value")
        .exchange()
        .expectStatus()
        .isOk();

    DESTINATIONS
        .listService()
        .verify(getRequestedFor(urlPathEqualTo("/lists")).withoutHeader("X-Hop"));
  }

  @Test
  void headerWithinConfiguredLimit_isForwarded() {
    DESTINATIONS.listService().stubFor(get(urlPathEqualTo("/lists")).willReturn(okJson("{}")));

    webTestClient
        .get()
        .uri("/lists")
        .header("X-Large", LARGE_HEADER_VALUE)
        .exchange()
        .expectStatus()
        .isOk();

    DESTINATIONS
        .listService()
        .verify(
            getRequestedFor(urlPathEqualTo("/lists"))
                .withHeader("X-Large", equalTo(LARGE_HEADER_VALUE)));
  }

  @Test
  void oversizedHeader_isRejected_withoutForwarding() {
    ExchangeResult result =
        webTestClient
            .get()
            .uri("/lists")
            .header("X-Large", "a".repeat(20 * 1024))
            .exchange()
            .expectStatus()
            .isEqualTo(431)
            .returnResult();

    assertThat(result.getResponseHeaders().get(HEADER)).isNull();
    DESTINATIONS.listService().verify(0, getRequestedFor(urlPathEqualTo("/lists")));
  }

  @Test
  void oversizedBody_isRejected() {
    ExchangeResult result =
        webTestClient
            .post()
            .uri("/lists")
            .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .bodyValue("{\"name\":\"" + "a".repeat(1024 * 1024) + "\"}")
            .exchange()
            .expectStatus()
            .isEqualTo(413)
            .returnResult();

    List<String> correlationIds = result.getResponseHeaders().get(HEADER);
    assertThat(correlationIds).hasSize(1);
    assertThat(result.getResponseHeaders().getContentType())
        .isNotEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    assertThat(result.getResponseBodyContent()).isEmpty();
    DESTINATIONS.listService().verify(0, postRequestedFor(urlPathEqualTo("/lists")));
  }

  @Test
  void relativeLocation_isForwardedUnchanged() {
    String location = "/lists/1b4e28ba-2fa1-11d2-883f-0016d3cca427";
    DESTINATIONS
        .listService()
        .stubFor(
            post(urlPathEqualTo("/lists"))
                .willReturn(
                    aResponse()
                        .withStatus(201)
                        .withHeader("Location", location)
                        .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .withBody("{}")));

    webTestClient
        .post()
        .uri("/lists")
        .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
        .bodyValue("{}")
        .exchange()
        .expectStatus()
        .isCreated()
        .expectHeader()
        .location(location);
  }

  private static int findClosedPort() {
    try (ServerSocket socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    } catch (IOException ex) {
      throw new UncheckedIOException(ex);
    }
  }
}
