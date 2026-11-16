package dev.ivborrezo.shoppinglist.gateway.security;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import dev.ivborrezo.shoppinglist.gateway.support.GatewayTestDestinations;
import dev.ivborrezo.shoppinglist.testsupport.TestJwtFactory;
import dev.ivborrezo.shoppinglist.testsupport.TestRsaKey;
import dev.ivborrezo.shoppinglist.testsupport.TestUsers;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.TestConstructor.AutowireMode;
import org.springframework.test.web.reactive.server.ExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Test de integración del canal {@code Bearer} sin estado del borde.
 *
 * <p>Cubre el consumo con token válido, que se reenvía intacto y no crea sesión ni exige CSRF, y el
 * rechazo uniforme {@code 401 UNAUTHENTICATED} en formato {@code ProblemDetail} sin redirección
 * para peticiones sin credenciales, con token expirado, con firma inválida, con emisor desconocido,
 * con sujeto no UUID o malformado.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@TestConstructor(autowireMode = AutowireMode.ALL)
class BearerSecurityIntegrationIT {

  private static final GatewayTestDestinations DESTINATIONS = GatewayTestDestinations.start();

  private final WebTestClient webTestClient;

  /**
   * Inyecta el cliente HTTP por constructor.
   *
   * @param webTestClient cliente contra el servidor real en puerto aleatorio
   */
  @Autowired
  BearerSecurityIntegrationIT(WebTestClient webTestClient) {
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
  void stubDestinations() {
    DESTINATIONS.productService().resetAll();
    DESTINATIONS.listService().resetAll();
    DESTINATIONS.listService().stubFor(get(urlPathEqualTo("/lists")).willReturn(okJson("{}")));
    DESTINATIONS
        .listService()
        .stubFor(
            post(urlPathEqualTo("/lists"))
                .willReturn(okJson("{}").withHeader("Content-Type", "application/json")));
  }

  @Test
  void validToken_isForwardedWithoutSessionOrCsrf() {
    String token = TestJwtFactory.token().subject(TestUsers.USER_1).build();

    webTestClient
        .get()
        .uri("/lists")
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
        .exchange()
        .expectStatus()
        .isOk()
        .expectCookie()
        .doesNotExist("SESSION");

    DESTINATIONS
        .listService()
        .verify(
            1,
            getRequestedFor(urlPathEqualTo("/lists"))
                .withHeader(HttpHeaders.AUTHORIZATION, equalTo("Bearer " + token)));
  }

  @Test
  void validToken_isForwardedForMutationsWithoutCsrf() {
    String token = TestJwtFactory.token().subject(TestUsers.USER_1).build();

    webTestClient
        .post()
        .uri("/lists")
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        .bodyValue("{\"name\":\"Compra\"}")
        .exchange()
        .expectStatus()
        .isOk();

    DESTINATIONS
        .listService()
        .verify(
            1,
            postRequestedFor(urlPathEqualTo("/lists"))
                .withHeader(HttpHeaders.AUTHORIZATION, equalTo("Bearer " + token)));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("rejectedTokens")
  void invalidCredentials_return401WithoutRedirect(String scenario, String authorization) {
    ExchangeResult result =
        webTestClient
            .get()
            .uri("/lists")
            .headers(headers -> headers.set(HttpHeaders.AUTHORIZATION, authorization))
            .exchange()
            .expectStatus()
            .isEqualTo(HttpStatus.UNAUTHORIZED)
            .returnResult();

    assertUnauthenticated(result);
    assertThat(DESTINATIONS.listService().getAllServeEvents()).isEmpty();
  }

  @Test
  void requestWithoutCredentials_returns401WithoutRedirect() {
    ExchangeResult result =
        webTestClient
            .get()
            .uri("/lists")
            .exchange()
            .expectStatus()
            .isEqualTo(HttpStatus.UNAUTHORIZED)
            .returnResult();

    assertUnauthenticated(result);
    assertThat(DESTINATIONS.listService().getAllServeEvents()).isEmpty();
  }

  private void assertUnauthenticated(ExchangeResult result) {
    assertThat(result.getResponseHeaders().getLocation()).as("sin redirección").isNull();
    assertThat(result.getResponseHeaders().getContentType())
        .as("formato del contrato de error")
        .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    assertThat(new String(result.getResponseBodyContent()))
        .contains("\"code\":\"UNAUTHENTICATED\"")
        .contains("\"status\":401");
  }

  private static Stream<Arguments> rejectedTokens() {
    return Stream.of(
        Arguments.of("malformed", "Bearer " + TestJwtFactory.malformed()),
        Arguments.of("expired", "Bearer " + TestJwtFactory.token().expired().build()),
        Arguments.of(
            "invalid signature",
            "Bearer " + TestJwtFactory.token().signedWith(TestRsaKey.alternative()).build()),
        Arguments.of(
            "unknown issuer",
            "Bearer " + TestJwtFactory.token().issuer("http://unknown.test").build()),
        Arguments.of(
            "non-UUID subject", "Bearer " + TestJwtFactory.token().subject("user-1").build()),
        Arguments.of("empty bearer", "Bearer "));
  }
}
