package dev.ivborrezo.shoppinglist.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ivborrezo.shoppinglist.gateway.support.GatewayTestDestinations;
import dev.ivborrezo.shoppinglist.gateway.support.OidcLoginTestClient;
import dev.ivborrezo.shoppinglist.gateway.support.TestOidcProvider;
import java.net.URI;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.TestConstructor.AutowireMode;
import org.springframework.test.web.reactive.server.ExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Test de integración del flujo de login OIDC del borde.
 *
 * <p>Cubre la redirección al proveedor con los parámetros del flujo Authorization Code, el
 * intercambio del código en el callback con emisión de una cookie de sesión {@code HttpOnly} y
 * {@code SameSite=Lax}, y el rechazo {@code 401 UNAUTHENTICATED} cuando el {@code state} no
 * coincide con la petición de autorización.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@TestConstructor(autowireMode = AutowireMode.ALL)
class OidcLoginFlowIntegrationIT {

  private static final GatewayTestDestinations DESTINATIONS = GatewayTestDestinations.start();

  private final WebTestClient webTestClient;

  private final TestOidcProvider provider;

  /**
   * Inyecta el cliente HTTP y el proveedor OIDC de test.
   *
   * @param webTestClient cliente contra el servidor real en puerto aleatorio
   */
  @Autowired
  OidcLoginFlowIntegrationIT(WebTestClient webTestClient) {
    this.webTestClient = webTestClient;
    this.provider = DESTINATIONS.oidcProvider();
  }

  @DynamicPropertySource
  static void destinations(DynamicPropertyRegistry registry) {
    DESTINATIONS.register(registry);
  }

  @AfterAll
  static void stopDestinations() {
    DESTINATIONS.stop();
  }

  @Test
  void login_redirectsToProviderWithAuthorizationCodeParams() {
    ExchangeResult result =
        webTestClient
            .get()
            .uri("/oauth2/authorization/keycloak")
            .exchange()
            .expectStatus()
            .isFound()
            .returnResult();

    URI location = result.getResponseHeaders().getLocation();
    assertThat(location).isNotNull();
    assertThat(location.toString()).startsWith(provider.authorizationUri());

    Map<String, String> query =
        UriComponentsBuilder.fromUri(location).build().getQueryParams().toSingleValueMap();
    assertThat(query.get("response_type")).isEqualTo("code");
    assertThat(query.get("client_id")).isEqualTo(TestOidcProvider.CLIENT_ID);
    assertThat(query.get("redirect_uri")).endsWith("/login/oauth2/code/keycloak");
    assertThat(query.get("scope")).contains("openid");
    assertThat(query.get("state")).isNotBlank();
    assertThat(query.get("nonce")).isNotBlank();
  }

  @Test
  void callback_exchangesCodeAndIssuesHttpOnlySessionCookie() {
    OidcLoginTestClient loginClient = new OidcLoginTestClient(webTestClient, provider);

    ExchangeResult callback = loginClient.callbackExpectingSession();

    ResponseCookie sessionCookie = callback.getResponseCookies().getFirst("SESSION");
    assertThat(sessionCookie).isNotNull();
    assertThat(sessionCookie.isHttpOnly()).isTrue();
    assertThat(sessionCookie.getSameSite()).isEqualTo("Lax");
    assertThat(new String(callback.getResponseBodyContent()))
        .doesNotContain(TestOidcProvider.ACCESS_TOKEN);
    assertThat(callback.getResponseHeaders().getFirst("Authorization")).isNull();
  }

  @Test
  void callback_withInvalidState_returns401() {
    OidcLoginTestClient loginClient = new OidcLoginTestClient(webTestClient, provider);
    OidcLoginTestClient.AuthorizationRequest authorization = loginClient.beginLogin();

    ExchangeResult result =
        webTestClient
            .get()
            .uri(
                uriBuilder ->
                    uriBuilder
                        .path("/login/oauth2/code/keycloak")
                        .queryParam("code", "test-authorization-code")
                        .queryParam("state", "not-the-issued-state")
                        .build())
            .cookie("SESSION", authorization.sessionCookie())
            .exchange()
            .expectStatus()
            .isEqualTo(HttpStatus.UNAUTHORIZED)
            .returnResult();

    assertThat(result.getResponseHeaders().getLocation()).isNull();
    assertThat(result.getResponseHeaders().getContentType())
        .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    assertThat(new String(result.getResponseBodyContent()))
        .contains("\"code\":\"UNAUTHENTICATED\"");
  }
}
