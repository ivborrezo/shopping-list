package dev.ivborrezo.shoppinglist.gateway.support;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ivborrezo.shoppinglist.testsupport.TestJwtFactory;
import dev.ivborrezo.shoppinglist.testsupport.TestUsers;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseCookie;
import org.springframework.test.web.reactive.server.ExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

/**
 * Cliente de test que ejecuta el flujo OIDC completo contra el borde.
 *
 * <p>Inicia el login, extrae {@code state} y {@code nonce} de la redirección al proveedor, prepara
 * el intercambio de tokens con ese {@code nonce}, invoca el callback conservando la cookie de
 * sesión y devuelve la sesión autenticada junto con el token CSRF de la cookie {@code XSRF-TOKEN}.
 * Permite además reproducir callbacks con tokens inválidos para verificar los rechazos.
 */
public final class OidcLoginTestClient {

  /** Nombre de la cookie de sesión del borde. */
  public static final String SESSION_COOKIE = "SESSION";

  /** Nombre de la cookie del token CSRF. */
  public static final String CSRF_COOKIE = "XSRF-TOKEN";

  /** Nombre de la cabecera con la que se envía el token CSRF. */
  public static final String CSRF_HEADER = "X-XSRF-TOKEN";

  private static final String REGISTRATION_ID = "keycloak";

  private final WebTestClient webTestClient;

  private final TestOidcProvider provider;

  /**
   * Crea el cliente de login contra el servidor y el proveedor indicados.
   *
   * @param webTestClient cliente HTTP contra el servidor real en puerto aleatorio
   * @param provider proveedor OIDC de test que sirve el intercambio de tokens
   */
  public OidcLoginTestClient(WebTestClient webTestClient, TestOidcProvider provider) {
    this.webTestClient = webTestClient;
    this.provider = provider;
  }

  /**
   * Ejecuta el login del usuario estándar de los tests.
   *
   * @return sesión autenticada con su cookie y su token CSRF
   */
  public LoginSession login() {
    return login(TestUsers.USER_1);
  }

  /**
   * Ejecuta el login del usuario indicado.
   *
   * @param userId identificador del usuario autenticado
   * @return sesión autenticada con su cookie y su token CSRF
   */
  public LoginSession login(UUID userId) {
    AuthorizationRequest authorization = beginLogin();
    provider.stubTokenExchange(authorization.nonce(), userId);
    ExchangeResult callback = completeLogin(authorization);
    assertThat(callback.getStatus().value()).as("redirección tras el callback").isEqualTo(302);
    return sessionOf(callback, authorization);
  }

  /**
   * Completa el callback del usuario estándar y devuelve la respuesta sin extraer cookies.
   *
   * @return respuesta del callback, que emite la cookie de sesión
   */
  public ExchangeResult callbackExpectingSession() {
    AuthorizationRequest authorization = beginLogin();
    provider.stubTokenExchange(authorization.nonce(), TestUsers.USER_1);
    ExchangeResult callback = completeLogin(authorization);
    assertThat(callback.getStatus().value()).as("redirección tras el callback").isEqualTo(302);
    return callback;
  }

  /**
   * Completa un callback con un {@code id_token} personalizado y devuelve el resultado.
   *
   * @param subject identificador que el proveedor devuelve en el userinfo
   * @param idTokenFactory fábrica del {@code id_token} con el emisor o el sujeto inválidos
   * @return respuesta del callback, que el test valida
   */
  public ExchangeResult loginExpectingRejection(UUID subject, TestJwtFactory idTokenFactory) {
    AuthorizationRequest authorization = beginLogin();
    provider.stubTokenExchange(authorization.nonce(), subject, idTokenFactory);
    return completeLogin(authorization);
  }

  /**
   * Envía una petición de cierre de sesión con la cookie y el token CSRF de la sesión.
   *
   * @param session sesión autenticada devuelta por {@link #login()}
   * @return respuesta del cierre de sesión
   */
  public ExchangeResult logout(LoginSession session) {
    return webTestClient
        .post()
        .uri("/logout")
        .cookie(SESSION_COOKIE, session.sessionCookie())
        .cookie(CSRF_COOKIE, session.csrfToken())
        .header(CSRF_HEADER, session.csrfToken())
        .exchange()
        .returnResult();
  }

  /**
   * Inicia el login y devuelve los datos necesarios para completar el callback.
   *
   * @return datos de la petición de autorización
   */
  public AuthorizationRequest beginLogin() {
    ExchangeResult authorization =
        webTestClient
            .get()
            .uri("/oauth2/authorization/" + REGISTRATION_ID)
            .exchange()
            .expectStatus()
            .isFound()
            .returnResult();

    String location = authorization.getResponseHeaders().getFirst("Location");
    assertThat(location).as("redirección al proveedor OIDC").isNotNull();
    Map<String, String> query =
        UriComponentsBuilder.fromUriString(location).build().getQueryParams().toSingleValueMap();
    String state = decode(query.get("state"));
    String nonce = decode(query.get("nonce"));
    assertThat(state).as("state de la petición de autorización").isNotNull();
    assertThat(nonce).as("nonce de la petición de autorización").isNotNull();

    return new AuthorizationRequest(
        state, nonce, cookie(authorization, SESSION_COOKIE), cookie(authorization, CSRF_COOKIE));
  }

  /**
   * Invoca el callback de OIDC conservando la cookie de sesión de la petición de autorización.
   *
   * @param authorization datos devueltos por {@link #beginLogin()}
   * @return respuesta del callback
   */
  public ExchangeResult completeLogin(AuthorizationRequest authorization) {
    return webTestClient
        .get()
        .uri(
            uriBuilder ->
                uriBuilder
                    .path("/login/oauth2/code/" + REGISTRATION_ID)
                    .queryParam("code", "test-authorization-code")
                    .queryParam("state", authorization.state())
                    .build())
        .cookie(SESSION_COOKIE, authorization.sessionCookie())
        .exchange()
        .returnResult();
  }

  private LoginSession sessionOf(ExchangeResult callback, AuthorizationRequest authorization) {
    String sessionCookie = cookie(callback, SESSION_COOKIE);
    if (sessionCookie == null) {
      sessionCookie = authorization.sessionCookie();
    }
    String csrfToken = cookie(callback, CSRF_COOKIE);
    if (csrfToken == null) {
      csrfToken = authorization.csrfToken();
    }
    assertThat(sessionCookie).as("cookie de sesión autenticada").isNotNull();
    assertThat(csrfToken).as("token CSRF de la sesión").isNotNull();
    return new LoginSession(sessionCookie, csrfToken);
  }

  private static String decode(String value) {
    return value != null ? UriUtils.decode(value, StandardCharsets.UTF_8) : null;
  }

  private static String cookie(ExchangeResult result, String name) {
    ResponseCookie cookie = result.getResponseCookies().getFirst(name);
    return cookie != null ? cookie.getValue() : null;
  }

  /**
   * Datos de la petición de autorización necesarios para completar el callback.
   *
   * @param state valor del parámetro {@code state}
   * @param nonce valor del parámetro {@code nonce}
   * @param sessionCookie cookie de sesión creada al iniciar el login
   * @param csrfToken token CSRF emitido junto con la petición de autorización
   */
  public record AuthorizationRequest(
      String state, String nonce, String sessionCookie, String csrfToken) {}

  /**
   * Sesión autenticada lista para acompañar peticiones posteriores.
   *
   * @param sessionCookie valor de la cookie de sesión
   * @param csrfToken valor del token CSRF de la cookie {@code XSRF-TOKEN}
   */
  public record LoginSession(String sessionCookie, String csrfToken) {}
}
