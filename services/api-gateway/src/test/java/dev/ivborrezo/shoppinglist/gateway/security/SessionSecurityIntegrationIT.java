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
import dev.ivborrezo.shoppinglist.gateway.support.MutableClock;
import dev.ivborrezo.shoppinglist.gateway.support.OidcLoginTestClient;
import dev.ivborrezo.shoppinglist.gateway.support.OidcLoginTestClient.LoginSession;
import dev.ivborrezo.shoppinglist.gateway.support.TestOidcProvider;
import dev.ivborrezo.shoppinglist.testsupport.TestJwtFactory;
import dev.ivborrezo.shoppinglist.testsupport.TestUsers;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.TestConstructor.AutowireMode;
import org.springframework.test.web.reactive.server.ExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.server.session.DefaultWebSessionManager;
import org.springframework.web.server.session.InMemoryWebSessionStore;
import org.springframework.web.server.session.WebSessionManager;

/**
 * Test de integración del canal de sesión OAuth2 BFF del borde.
 *
 * <p>Cubre el relay del token de acceso hacia el servicio de destino, la exigencia de CSRF en
 * mutaciones con sesión, la precedencia del {@code 401} sobre el {@code 403}, el cierre de sesión
 * con invalidación de la cookie, la expiración por inactividad y el rechazo de sesiones con sujeto
 * no UUID o con un {@code id_token} de emisor desconocido.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@TestConstructor(autowireMode = AutowireMode.ALL)
class SessionSecurityIntegrationIT {

  private static final String SESSION_COOKIE = OidcLoginTestClient.SESSION_COOKIE;

  private static final String CSRF_COOKIE = OidcLoginTestClient.CSRF_COOKIE;

  private static final String CSRF_HEADER = OidcLoginTestClient.CSRF_HEADER;

  private static final GatewayTestDestinations DESTINATIONS = GatewayTestDestinations.start();

  private final WebTestClient webTestClient;

  private final OidcLoginTestClient loginClient;

  private final WebSessionManager sessionManager;

  private MutableClock sessionClock;

  /**
   * Inyecta el cliente HTTP, el gestor de sesiones y prepara el flujo de login.
   *
   * @param webTestClient cliente contra el servidor real en puerto aleatorio
   * @param sessionManager gestor de sesiones del contexto
   */
  @Autowired
  SessionSecurityIntegrationIT(WebTestClient webTestClient, WebSessionManager sessionManager) {
    this.webTestClient = webTestClient;
    this.sessionManager = sessionManager;
    this.loginClient = new OidcLoginTestClient(webTestClient, DESTINATIONS.oidcProvider());
  }

  @DynamicPropertySource
  static void destinations(DynamicPropertyRegistry registry) {
    DESTINATIONS.register(registry);
    registry.add("api-gateway.security.session-ttl", () -> "2s");
  }

  @AfterAll
  static void stopDestinations() {
    DESTINATIONS.stop();
  }

  @BeforeEach
  void stubDestinations() {
    sessionClock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
    sessionStore().setClock(sessionClock);
    DESTINATIONS.productService().resetAll();
    DESTINATIONS.listService().resetAll();
    DESTINATIONS.listService().stubFor(get(urlPathEqualTo("/lists")).willReturn(okJson("{}")));
    DESTINATIONS
        .listService()
        .stubFor(
            post(urlPathEqualTo("/lists"))
                .willReturn(
                    okJson("{}")
                        .withHeader("Location", "/lists/1b4e28ba-2fa1-11d2-883f-0016d3cca427")));
  }

  @Test
  void sessionRequest_relaysBearerTokenToDownstream() {
    LoginSession session = loginClient.login();

    webTestClient
        .get()
        .uri("/lists")
        .cookie(SESSION_COOKIE, session.sessionCookie())
        .exchange()
        .expectStatus()
        .isOk();

    DESTINATIONS
        .listService()
        .verify(
            1,
            getRequestedFor(urlPathEqualTo("/lists"))
                .withHeader(
                    HttpHeaders.AUTHORIZATION, equalTo("Bearer " + TestOidcProvider.ACCESS_TOKEN)));
  }

  @Test
  void mutationWithoutCsrf_returns403AccessDenied() {
    LoginSession session = loginClient.login();

    ExchangeResult result =
        webTestClient
            .post()
            .uri("/lists")
            .cookie(SESSION_COOKIE, session.sessionCookie())
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .bodyValue("{\"name\":\"Compra\"}")
            .exchange()
            .expectStatus()
            .isForbidden()
            .returnResult();

    assertThat(result.getResponseHeaders().getContentType())
        .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    assertThat(new String(result.getResponseBodyContent()))
        .contains("\"code\":\"ACCESS_DENIED\"")
        .contains("\"status\":403");
    assertThat(DESTINATIONS.listService().getAllServeEvents()).isEmpty();
  }

  @Test
  void mutationWithValidCsrf_isForwarded() {
    LoginSession session = loginClient.login();

    webTestClient
        .post()
        .uri("/lists")
        .cookie(SESSION_COOKIE, session.sessionCookie())
        .cookie(CSRF_COOKIE, session.csrfToken())
        .header(CSRF_HEADER, session.csrfToken())
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
                .withHeader(
                    HttpHeaders.AUTHORIZATION, equalTo("Bearer " + TestOidcProvider.ACCESS_TOKEN)));
  }

  @Test
  void postWithoutSession_returns401Not403() {
    ExchangeResult result =
        webTestClient
            .post()
            .uri("/lists")
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .bodyValue("{\"name\":\"Compra\"}")
            .exchange()
            .expectStatus()
            .isEqualTo(HttpStatus.UNAUTHORIZED)
            .returnResult();

    assertThat(new String(result.getResponseBodyContent()))
        .contains("\"code\":\"UNAUTHENTICATED\"");
    assertThat(result.getResponseHeaders().getLocation()).isNull();
    assertThat(DESTINATIONS.listService().getAllServeEvents()).isEmpty();
  }

  @Test
  void logout_invalidatesSessionAndClearsCookie() {
    LoginSession session = loginClient.login();

    ExchangeResult result = loginClient.logout(session);

    assertThat(result.getStatus()).isEqualTo(HttpStatus.NO_CONTENT);

    ResponseCookie cleared = result.getResponseCookies().getFirst(SESSION_COOKIE);
    assertThat(cleared).isNotNull();
    assertThat(cleared.getMaxAge()).isEqualTo(Duration.ZERO);
    assertThat(cleared.getValue()).isEmpty();
  }

  @Test
  void oldCookieAfterLogout_returns401() {
    LoginSession session = loginClient.login();
    assertThat(loginClient.logout(session).getStatus()).isEqualTo(HttpStatus.NO_CONTENT);

    webTestClient
        .get()
        .uri("/lists")
        .cookie(SESSION_COOKIE, session.sessionCookie())
        .exchange()
        .expectStatus()
        .isEqualTo(HttpStatus.UNAUTHORIZED)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("UNAUTHENTICATED");
  }

  @Test
  void expiredSession_returns401() {
    LoginSession session = loginClient.login();

    sessionClock.advance(Duration.ofSeconds(3));

    webTestClient
        .get()
        .uri("/lists")
        .cookie(SESSION_COOKIE, session.sessionCookie())
        .exchange()
        .expectStatus()
        .isEqualTo(HttpStatus.UNAUTHORIZED)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("UNAUTHENTICATED");
    assertThat(DESTINATIONS.listService().getAllServeEvents()).isEmpty();
  }

  private InMemoryWebSessionStore sessionStore() {
    return (InMemoryWebSessionStore) ((DefaultWebSessionManager) sessionManager).getSessionStore();
  }

  @Test
  void sessionWithNonUuidSubject_returns401() {
    ExchangeResult callback =
        loginClient.loginExpectingRejection(
            TestUsers.USER_1, TestJwtFactory.token().subject("not-a-uuid"));

    assertThat(callback.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(new String(callback.getResponseBodyContent()))
        .contains("\"code\":\"UNAUTHENTICATED\"");
  }

  @Test
  void idTokenWithUnknownIssuer_returns401() {
    ExchangeResult callback =
        loginClient.loginExpectingRejection(
            TestUsers.USER_1, TestJwtFactory.token().issuer("http://unknown-issuer.test"));

    assertThat(callback.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(new String(callback.getResponseBodyContent()))
        .contains("\"code\":\"UNAUTHENTICATED\"");
  }
}
