package dev.ivborrezo.shoppinglist.gateway.routing;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import dev.ivborrezo.shoppinglist.gateway.support.GatewayTestDestinations;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.TestConstructor.AutowireMode;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Test de integración del enrutado público del gateway.
 *
 * <p>Ejercita las rutas reales contra dos WireMock y cubre los criterios de aceptación de H-01:
 * lecturas y escrituras llegan al servicio correcto con el path íntegro, y un path no expuesto
 * responde 404 sin reenviarse. Incluye la guarda de que la tabla tiene exactamente dos rutas.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@TestConstructor(autowireMode = AutowireMode.ALL)
class RoutingIntegrationIT {

  private static final GatewayTestDestinations DESTINATIONS = GatewayTestDestinations.start();

  private static final UUID OWNER_ID = UUID.fromString("3f2504e0-4f89-41d3-9a0c-0305e82c3301");

  private static final UUID LIST_ID = UUID.fromString("1b4e28ba-2fa1-11d2-883f-0016d3cca427");

  private static final UUID PRODUCT_ID = UUID.fromString("5b597bf7-446e-463e-a2f9-839c360a01cc");

  private final WebTestClient webTestClient;

  private final RouteDefinitionLocator routeDefinitionLocator;

  /**
   * Inyecta las dependencias de test por constructor.
   *
   * @param webTestClient cliente HTTP contra el servidor real en puerto aleatorio
   * @param routeDefinitionLocator localizador de las rutas declaradas en configuración
   */
  @Autowired
  RoutingIntegrationIT(WebTestClient webTestClient, RouteDefinitionLocator routeDefinitionLocator) {
    this.webTestClient = webTestClient;
    this.routeDefinitionLocator = routeDefinitionLocator;
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
    DESTINATIONS.listService().resetAll();
  }

  @Test
  void routeTable_hasExactlyTwoRoutes() {
    assertThat(
            routeDefinitionLocator
                .getRouteDefinitions()
                .map(route -> route.getId())
                .collectList()
                .block())
        .containsExactly("product-service", "list-service");
  }

  @Test
  void categories_areForwardedToProductService() {
    String responseBody = "{\"content\":[],\"page\":0,\"size\":20,\"totalElements\":0}";
    DESTINATIONS
        .productService()
        .stubFor(get(urlPathEqualTo("/categories")).willReturn(okJson(responseBody)));

    webTestClient
        .get()
        .uri("/categories")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(String.class)
        .isEqualTo(responseBody);

    DESTINATIONS.productService().verify(getRequestedFor(urlEqualTo("/categories")));
    DESTINATIONS.listService().verify(0, getRequestedFor(urlEqualTo("/categories")));
  }

  @Test
  void lists_areForwardedToListService() {
    String responseBody = "{\"content\":[],\"page\":0,\"size\":20,\"totalElements\":0}";
    DESTINATIONS
        .listService()
        .stubFor(get(urlPathEqualTo("/lists")).willReturn(okJson(responseBody)));

    webTestClient
        .get()
        .uri(uriBuilder -> uriBuilder.path("/lists").queryParam("ownerId", OWNER_ID).build())
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(String.class)
        .isEqualTo(responseBody);

    DESTINATIONS
        .listService()
        .verify(
            getRequestedFor(urlPathEqualTo("/lists"))
                .withQueryParam("ownerId", equalTo(OWNER_ID.toString())));
    DESTINATIONS.listService().verify(1, getRequestedFor(urlPathEqualTo("/lists")));
    DESTINATIONS.productService().verify(0, getRequestedFor(urlPathEqualTo("/lists")));
  }

  @Test
  void createList_isForwardedToListService() {
    String requestBody = "{\"ownerId\":\"" + OWNER_ID + "\",\"name\":\"Compra de la semana\"}";
    String responseBody =
        "{\"id\":\""
            + LIST_ID
            + "\",\"ownerId\":\""
            + OWNER_ID
            + "\",\"name\":\"Compra de la semana\",\"items\":[]}";
    DESTINATIONS
        .listService()
        .stubFor(
            post(urlEqualTo("/lists"))
                .willReturn(
                    aResponse()
                        .withStatus(201)
                        .withHeader("Location", "/lists/" + LIST_ID)
                        .withHeader("Content-Type", "application/json")
                        .withBody(responseBody)));

    webTestClient
        .post()
        .uri("/lists")
        .header("Content-Type", "application/json")
        .bodyValue(requestBody)
        .exchange()
        .expectStatus()
        .isCreated()
        .expectHeader()
        .location("/lists/" + LIST_ID)
        .expectBody(String.class)
        .isEqualTo(responseBody);

    DESTINATIONS
        .listService()
        .verify(postRequestedFor(urlEqualTo("/lists")).withRequestBody(equalToJson(requestBody)));
  }

  @Test
  void parameterizedRoutes_keepTheirPathAndQuery() {
    DESTINATIONS
        .listService()
        .stubFor(get(urlPathEqualTo("/lists/" + LIST_ID)).willReturn(okJson("{}")));
    DESTINATIONS
        .listService()
        .stubFor(post(urlPathEqualTo("/lists/" + LIST_ID + "/items")).willReturn(okJson("{}")));
    DESTINATIONS
        .productService()
        .stubFor(
            post(urlPathEqualTo("/user-products/" + PRODUCT_ID + "/favorite"))
                .willReturn(okJson("{}")));
    DESTINATIONS
        .productService()
        .stubFor(get(urlPathEqualTo("/user-products/favorites")).willReturn(okJson("{}")));

    webTestClient.get().uri("/lists/" + LIST_ID).exchange().expectStatus().isOk();
    webTestClient
        .post()
        .uri(
            uriBuilder ->
                uriBuilder
                    .path("/lists/" + LIST_ID + "/items")
                    .queryParam("ownerId", OWNER_ID)
                    .build())
        .exchange()
        .expectStatus()
        .isOk();
    webTestClient
        .post()
        .uri(
            uriBuilder ->
                uriBuilder
                    .path("/user-products/" + PRODUCT_ID + "/favorite")
                    .queryParam("ownerId", OWNER_ID)
                    .queryParam("productType", "BASE")
                    .build())
        .exchange()
        .expectStatus()
        .isOk();
    webTestClient
        .get()
        .uri(
            uriBuilder ->
                uriBuilder.path("/user-products/favorites").queryParam("ownerId", OWNER_ID).build())
        .exchange()
        .expectStatus()
        .isOk();

    DESTINATIONS.listService().verify(getRequestedFor(urlPathEqualTo("/lists/" + LIST_ID)));
    DESTINATIONS
        .listService()
        .verify(
            postRequestedFor(urlPathEqualTo("/lists/" + LIST_ID + "/items"))
                .withQueryParam("ownerId", equalTo(OWNER_ID.toString())));
    DESTINATIONS
        .productService()
        .verify(
            postRequestedFor(urlPathEqualTo("/user-products/" + PRODUCT_ID + "/favorite"))
                .withQueryParam("ownerId", equalTo(OWNER_ID.toString()))
                .withQueryParam("productType", equalTo("BASE")));
    DESTINATIONS
        .productService()
        .verify(
            getRequestedFor(urlPathEqualTo("/user-products/favorites"))
                .withQueryParam("ownerId", equalTo(OWNER_ID.toString())));
  }

  @Test
  void unknownPath_isNotForwarded() {
    webTestClient.get().uri("/dataviz").exchange().expectStatus().isNotFound();

    DESTINATIONS.productService().verify(0, getRequestedFor(urlPathEqualTo("/dataviz")));
    DESTINATIONS.listService().verify(0, getRequestedFor(urlPathEqualTo("/dataviz")));
    assertThat(DESTINATIONS.productService().getAllServeEvents()).isEmpty();
    assertThat(DESTINATIONS.listService().getAllServeEvents()).isEmpty();
  }
}
