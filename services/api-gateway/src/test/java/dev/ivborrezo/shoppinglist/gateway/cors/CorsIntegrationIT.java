package dev.ivborrezo.shoppinglist.gateway.cors;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import dev.ivborrezo.shoppinglist.gateway.correlation.CorrelationContext;
import dev.ivborrezo.shoppinglist.gateway.support.GatewayTestDestinations;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.cloud.gateway.config.GlobalCorsProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.TestConstructor.AutowireMode;
import org.springframework.test.web.reactive.server.ExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.util.CollectionUtils;

/**
 * Test de integración de la política CORS del borde.
 *
 * <p>Cubre H-02: el preflight se responde en el borde, también para un path que ningún predicate
 * captura; las peticiones reales de un origen permitido reciben el eco de correlación expuesto y
 * ninguna credencial; un origen fuera de la whitelist se rechaza sin reenviar la petición.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@TestConstructor(autowireMode = AutowireMode.ALL)
class CorsIntegrationIT {

  private static final String CORS_CONFIGURATION =
      "spring.cloud.gateway.server.webflux.globalcors.cors-configurations.[/**]";

  private static final String ALLOWED_ORIGIN = "http://app.test";

  private static final String SECOND_ALLOWED_ORIGIN = "http://preview.test";

  private static final String DENIED_ORIGIN = "http://unknown.test";

  private static final GatewayTestDestinations DESTINATIONS = GatewayTestDestinations.start();

  private final WebTestClient webTestClient;

  private final GlobalCorsProperties globalCorsProperties;

  /**
   * Inyecta el cliente HTTP y las propiedades CORS del contexto real.
   *
   * @param webTestClient cliente contra el servidor real en puerto aleatorio
   * @param globalCorsProperties propiedades globales enlazadas desde la configuración
   */
  @Autowired
  CorsIntegrationIT(WebTestClient webTestClient, GlobalCorsProperties globalCorsProperties) {
    this.webTestClient = webTestClient;
    this.globalCorsProperties = globalCorsProperties;
  }

  @DynamicPropertySource
  static void corsProperties(DynamicPropertyRegistry registry) {
    DESTINATIONS.register(registry);
    registry.add(CORS_CONFIGURATION + ".allowed-origins[0]", () -> ALLOWED_ORIGIN);
    registry.add(CORS_CONFIGURATION + ".allowed-origins[1]", () -> SECOND_ALLOWED_ORIGIN);
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
  void preflightForRoutedPath_isAnsweredAtTheBorderWithoutForwarding() {
    ExchangeResult result =
        webTestClient
            .options()
            .uri("/lists")
            .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
            .header(
                HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
                "Content-Type, Accept-Language, X-Correlation-Id")
            .exchange()
            .expectStatus()
            .isOk()
            .returnResult();

    assertAllowedCors(result);
    assertAllowedMethods(result);
    assertAllowedHeaders(result);
    assertMaxAge(result);
    assertCorrelationHeader(result);
    verifyNotForwarded("/lists");
  }

  @Test
  void preflightForUnroutedPath_isAnsweredAtTheBorder() {
    ExchangeResult result =
        webTestClient
            .options()
            .uri("/dataviz")
            .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
            .exchange()
            .expectStatus()
            .isOk()
            .returnResult();

    assertAllowedCors(result);
    assertAllowedMethods(result);
    assertCorrelationHeader(result);
    verifyNotForwarded("/dataviz");
  }

  @Test
  void simpleRequestFromAllowedOrigin_receivesExposedCorrelationId() {
    ExchangeResult result =
        webTestClient
            .get()
            .uri("/lists")
            .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
            .exchange()
            .expectStatus()
            .isOk()
            .returnResult();

    assertAllowedCors(result);
    assertCorrelationHeader(result);
    assertThat(DESTINATIONS.listService().getAllServeEvents()).hasSize(1);
  }

  @Test
  void deniedOrigin_preflight_isRejectedWithoutForwarding() {
    ExchangeResult result =
        webTestClient
            .options()
            .uri("/lists")
            .header(HttpHeaders.ORIGIN, DENIED_ORIGIN)
            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
            .exchange()
            .expectStatus()
            .isForbidden()
            .returnResult();

    assertNoCorsHeaders(result);
    verifyNotForwarded("/lists");
  }

  @Test
  void deniedOrigin_simpleRequest_isRejectedWithoutForwarding() {
    ExchangeResult result =
        webTestClient
            .get()
            .uri("/lists")
            .header(HttpHeaders.ORIGIN, DENIED_ORIGIN)
            .exchange()
            .expectStatus()
            .isForbidden()
            .returnResult();

    assertNoCorsHeaders(result);
    assertThat(result.getResponseHeaders().getContentType())
        .isNotEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    verifyNotForwarded("/lists");
  }

  @Test
  void configuredOrigins_areLoadedAndDoNotContainWildcard() {
    List<String> origins =
        globalCorsProperties.getCorsConfigurations().values().stream()
            .flatMap(
                configuration ->
                    CollectionUtils.isEmpty(configuration.getAllowedOrigins())
                        ? Stream.empty()
                        : configuration.getAllowedOrigins().stream())
            .toList();

    assertThat(origins).contains(ALLOWED_ORIGIN, SECOND_ALLOWED_ORIGIN).doesNotContain("*");
  }

  @Test
  void varyOrigin_isPresentOnReflectedResponses() {
    ExchangeResult preflight =
        webTestClient
            .options()
            .uri("/lists")
            .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
            .exchange()
            .expectStatus()
            .isOk()
            .returnResult();
    ExchangeResult simpleRequest =
        webTestClient
            .get()
            .uri("/lists")
            .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
            .exchange()
            .expectStatus()
            .isOk()
            .returnResult();

    assertThat(preflight.getResponseHeaders().getVary()).contains(HttpHeaders.ORIGIN);
    assertThat(simpleRequest.getResponseHeaders().getVary()).contains(HttpHeaders.ORIGIN);
  }

  private void assertAllowedCors(ExchangeResult result) {
    HttpHeaders headers = result.getResponseHeaders();
    assertThat(headers.getAccessControlAllowOrigin()).isEqualTo(ALLOWED_ORIGIN);
    assertThat(headers.getAccessControlExposeHeaders())
        .containsExactly(CorrelationContext.HEADER_NAME);
    assertThat(headers.getAccessControlAllowCredentials()).isFalse();
  }

  private void assertAllowedMethods(ExchangeResult result) {
    assertThat(result.getResponseHeaders().getFirst(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS))
        .contains("GET", "POST", "PATCH", "DELETE");
  }

  private void assertAllowedHeaders(ExchangeResult result) {
    assertThat(result.getResponseHeaders().getFirst(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS))
        .contains("Content-Type", "Accept-Language", CorrelationContext.HEADER_NAME);
  }

  private void assertMaxAge(ExchangeResult result) {
    assertThat(result.getResponseHeaders().getFirst(HttpHeaders.ACCESS_CONTROL_MAX_AGE))
        .isEqualTo("1800");
  }

  private void assertCorrelationHeader(ExchangeResult result) {
    List<String> values = result.getResponseHeaders().get(CorrelationContext.HEADER_NAME);
    assertThat(values).hasSize(1);
    assertThat(UUID.fromString(values.getFirst())).isNotNull();
  }

  private void assertNoCorsHeaders(ExchangeResult result) {
    HttpHeaders headers = result.getResponseHeaders();
    assertThat(headers.getAccessControlAllowOrigin()).isNull();
    assertThat(headers.getAccessControlAllowCredentials()).isFalse();
  }

  private void verifyNotForwarded(String path) {
    DESTINATIONS.listService().verify(0, getRequestedFor(urlPathEqualTo(path)));
    DESTINATIONS.productService().verify(0, getRequestedFor(urlPathEqualTo(path)));
  }
}
