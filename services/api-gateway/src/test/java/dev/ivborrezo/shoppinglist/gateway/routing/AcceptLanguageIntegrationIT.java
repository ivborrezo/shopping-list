package dev.ivborrezo.shoppinglist.gateway.routing;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
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
 * Test de integración de la propagación de {@code Accept-Language} por el gateway.
 *
 * <p>Cubre H-05 y RN-03: el borde no parsea q-values, no renormaliza y no añade una cabecera de
 * idioma cuando la petición no la trae.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@TestConstructor(autowireMode = AutowireMode.ALL)
class AcceptLanguageIntegrationIT {

  private static final GatewayTestDestinations DESTINATIONS = GatewayTestDestinations.start();

  private final WebTestClient webTestClient;

  /**
   * Inyecta el cliente HTTP por constructor.
   *
   * @param webTestClient cliente contra el servidor real en puerto aleatorio
   */
  @Autowired
  AcceptLanguageIntegrationIT(WebTestClient webTestClient) {
    this.webTestClient = webTestClient;
  }

  @DynamicPropertySource
  static void destinations(DynamicPropertyRegistry registry) {
    DESTINATIONS.register(registry);
  }

  @AfterAll
  static void stopDestinations() {
    DESTINATIONS.stop();
  }

  @BeforeEach
  void resetStubs() {
    DESTINATIONS.productService().resetAll();
    DESTINATIONS
        .productService()
        .stubFor(get(urlPathEqualTo("/categories")).willReturn(okJson("{}")));
  }

  @Test
  void acceptLanguage_isForwardedUnchanged() {
    webTestClient
        .get()
        .uri("/categories")
        .header("Accept-Language", "es")
        .exchange()
        .expectStatus()
        .isOk();

    DESTINATIONS
        .productService()
        .verify(
            getRequestedFor(urlPathEqualTo("/categories"))
                .withHeader("Accept-Language", equalTo("es")));
  }

  @Test
  void acceptLanguageWithQualityValues_isNotRenormalized() {
    webTestClient
        .get()
        .uri("/categories")
        .header("Accept-Language", "es,en;q=0.8")
        .exchange()
        .expectStatus()
        .isOk();

    DESTINATIONS
        .productService()
        .verify(
            getRequestedFor(urlPathEqualTo("/categories"))
                .withHeader("Accept-Language", equalTo("es,en;q=0.8")));
  }

  @Test
  void withoutAcceptLanguage_noLanguageHeaderIsAdded() {
    webTestClient.get().uri("/categories").exchange().expectStatus().isOk();

    DESTINATIONS
        .productService()
        .verify(getRequestedFor(urlPathEqualTo("/categories")).withoutHeader("Accept-Language"));
  }
}
