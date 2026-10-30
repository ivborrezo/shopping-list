package dev.ivborrezo.shoppinglist.gateway.error;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import dev.ivborrezo.shoppinglist.gateway.support.GatewayTestDestinations;
import dev.ivborrezo.shoppinglist.gateway.support.InternalErrorTestConfiguration;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.TestConstructor.AutowireMode;
import org.springframework.test.web.reactive.server.ExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Test de integración del contrato de error propio del borde.
 *
 * <p>Cubre H-07: el 404 de ruta no expuesta, el 503 por destino inalcanzable y el 500 interno
 * responden con {@code ProblemDetail} y eco de correlación; los errores del destino cruzan
 * intactos.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@Import(InternalErrorTestConfiguration.class)
@TestConstructor(autowireMode = AutowireMode.ALL)
class ErrorContractIntegrationIT {

  private static final String HEADER = "X-Correlation-Id";

  private static final int CLOSED_PORT = findClosedPort();

  private static final GatewayTestDestinations DESTINATIONS = GatewayTestDestinations.start();

  private final WebTestClient webTestClient;

  /**
   * Inyecta el cliente HTTP por constructor.
   *
   * @param webTestClient cliente contra el servidor real en puerto aleatorio
   */
  @Autowired
  ErrorContractIntegrationIT(WebTestClient webTestClient) {
    this.webTestClient = webTestClient;
  }

  @DynamicPropertySource
  static void destinations(DynamicPropertyRegistry registry) {
    DESTINATIONS.register(registry);
    registry.add("list-service.client.base-url", () -> "http://127.0.0.1:" + CLOSED_PORT);
  }

  @AfterAll
  static void stopDestinations() {
    DESTINATIONS.stop();
  }

  @BeforeEach
  void resetStubs() {
    DESTINATIONS.productService().resetAll();
    DESTINATIONS.listService().resetAll();
  }

  @Test
  void unroutedPath_structureMatchesProblemDetailContract() {
    ExchangeResult result =
        webTestClient.get().uri("/dataviz").exchange().expectStatus().isNotFound().returnResult();

    assertThat(result.getResponseHeaders().getContentType())
        .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    assertThat(result.getResponseBodyContent()).isNotEmpty();
    String body = new String(result.getResponseBodyContent());
    assertThat(body)
        .contains("\"type\":\"about:blank\"")
        .contains("\"title\":\"Route not found\"")
        .contains("\"status\":404")
        .contains("\"instance\":\"/dataviz\"")
        .contains("\"code\":\"ROUTE_NOT_FOUND\"");
    assertCorrelationHeader(result);
    assertThat(DESTINATIONS.productService().getAllServeEvents()).isEmpty();
    assertThat(DESTINATIONS.listService().getAllServeEvents()).isEmpty();
  }

  @Test
  void unreachableDestination_returns503_withoutTopology() {
    ExchangeResult result =
        webTestClient.get().uri("/lists").exchange().expectStatus().isEqualTo(503).returnResult();

    String body = new String(result.getResponseBodyContent());
    assertThat(result.getResponseHeaders().getContentType())
        .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    assertThat(body)
        .contains("\"title\":\"Service temporarily unavailable\"")
        .contains("\"code\":\"SERVICE_UNAVAILABLE\"")
        .doesNotContain("127.0.0.1")
        .doesNotContain(String.valueOf(CLOSED_PORT));
    assertCorrelationHeader(result);
  }

  @Test
  void serviceError_passesThroughUnchanged() {
    String errorBody =
        """
        {"type":"about:blank","title":"Validation failed","status":400,"detail":"Invalid request",
        "instance":"/categories","code":"VALIDATION_FAILED"}
        """;
    DESTINATIONS
        .productService()
        .stubFor(
            get(urlPathEqualTo("/categories"))
                .willReturn(
                    aResponse()
                        .withStatus(400)
                        .withHeader("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE)
                        .withBody(errorBody)));

    ExchangeResult result =
        webTestClient
            .get()
            .uri("/categories")
            .exchange()
            .expectStatus()
            .isBadRequest()
            .returnResult();

    assertThat(result.getResponseHeaders().getContentType())
        .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    assertThat(new String(result.getResponseBodyContent())).isEqualTo(errorBody);
  }

  @Test
  void internalError_doesNotLeakMessage() {
    ExchangeResult result =
        webTestClient
            .get()
            .uri("/categories")
            .header("X-Test-Trigger", "internal-error")
            .exchange()
            .expectStatus()
            .isEqualTo(500)
            .returnResult();

    String body = new String(result.getResponseBodyContent());
    assertThat(body)
        .contains("\"title\":\"Internal Server Error\"")
        .contains("\"code\":\"INTERNAL_ERROR\"")
        .doesNotContain("Sensitive internal failure");
    assertCorrelationHeader(result);
  }

  @Test
  void destination5xx_passesThroughUnchanged() {
    String errorBody =
        """
        {"type":"about:blank","title":"Product service unavailable","status":503,
        "code":"PRODUCT_SERVICE_UNAVAILABLE"}
        """;
    DESTINATIONS
        .productService()
        .stubFor(
            get(urlPathEqualTo("/base-products"))
                .willReturn(
                    aResponse()
                        .withStatus(503)
                        .withHeader("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE)
                        .withBody(errorBody)));

    ExchangeResult result =
        webTestClient
            .get()
            .uri("/base-products")
            .exchange()
            .expectStatus()
            .isEqualTo(503)
            .returnResult();

    assertThat(new String(result.getResponseBodyContent())).isEqualTo(errorBody);
    assertThat(new String(result.getResponseBodyContent()))
        .contains("\"code\":\"PRODUCT_SERVICE_UNAVAILABLE\"")
        .doesNotContain("\"code\":\"SERVICE_UNAVAILABLE\"");
  }

  @Test
  void ownErrors_carryCorrelationId() {
    ExchangeResult notFound =
        webTestClient.get().uri("/dataviz").exchange().expectStatus().isNotFound().returnResult();
    ExchangeResult unavailable =
        webTestClient.get().uri("/lists").exchange().expectStatus().isEqualTo(503).returnResult();
    ExchangeResult internalError =
        webTestClient
            .get()
            .uri("/categories")
            .header("X-Test-Trigger", "internal-error")
            .exchange()
            .expectStatus()
            .isEqualTo(500)
            .returnResult();

    assertCorrelationHeader(notFound);
    assertCorrelationHeader(unavailable);
    assertCorrelationHeader(internalError);
  }

  private void assertCorrelationHeader(ExchangeResult result) {
    List<String> values = result.getResponseHeaders().get(HEADER);
    assertThat(values).hasSize(1);
    assertThat(UUID.fromString(values.getFirst())).isNotNull();
  }

  private static int findClosedPort() {
    try (ServerSocket socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    } catch (IOException ex) {
      throw new UncheckedIOException(ex);
    }
  }
}
