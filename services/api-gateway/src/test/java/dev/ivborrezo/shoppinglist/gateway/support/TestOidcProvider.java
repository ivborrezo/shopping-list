package dev.ivborrezo.shoppinglist.gateway.support;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import dev.ivborrezo.shoppinglist.testsupport.TestJwtFactory;
import dev.ivborrezo.shoppinglist.testsupport.TestRsaKey;
import dev.ivborrezo.shoppinglist.testsupport.TestUsers;
import java.util.UUID;

/**
 * Proveedor OIDC de test que sirve el JWKS y el intercambio de tokens con WireMock.
 *
 * <p>El emisor es estable ({@link TestUsers#TEST_ISSUER}) mientras que las URIs se resuelven en un
 * puerto dinámico. El {@code id_token} se firma con el par RSA compartido por los servicios y se
 * emite con el {@code nonce} de la petición de autorización, de modo que el flujo completo de login
 * OIDC se ejercita sin levantar Keycloak. El token de acceso es opaco y conocido para poder
 * verificar el relay y la ausencia de secretos en los logs.
 */
public final class TestOidcProvider {

  /** Identificador del cliente OAuth2 configurado en el borde. */
  public static final String CLIENT_ID = "gateway-client";

  private static final String CANARY_PREFIX = "test-canary-";

  /** Secreto del cliente OAuth2 configurado en el borde. */
  public static final String CLIENT_SECRET = CANARY_PREFIX + "client-credential";

  /** Token de acceso opaco que devuelve el proveedor de test. */
  public static final String ACCESS_TOKEN = CANARY_PREFIX + "access-credential";

  private static final String REALM_PATH = "/realms/shopping-list-test";

  private static final String JWKS_PATH = REALM_PATH + "/protocol/openid-connect/certs";

  private static final String AUTHORIZATION_PATH = REALM_PATH + "/protocol/openid-connect/auth";

  private static final String TOKEN_PATH = REALM_PATH + "/protocol/openid-connect/token";

  private static final String USER_INFO_PATH = REALM_PATH + "/protocol/openid-connect/userinfo";

  private final WireMockServer server;

  private TestOidcProvider(WireMockServer server) {
    this.server = server;
  }

  /**
   * Arranca el proveedor y publica el JWKS del par RSA compartido.
   *
   * @return proveedor arrancado en un puerto dinámico
   */
  public static TestOidcProvider start() {
    WireMockServer server = new WireMockServer(WireMockConfiguration.options().dynamicPort());
    server.start();
    server.stubFor(
        get(urlPathEqualTo(JWKS_PATH))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(TestRsaKey.primary().toJwks())));
    return new TestOidcProvider(server);
  }

  /** Detiene el proveedor. */
  public void stop() {
    server.stop();
  }

  /**
   * Emisor OIDC estable de los tokens de test.
   *
   * @return identificador del emisor
   */
  public String issuer() {
    return TestUsers.TEST_ISSUER;
  }

  /**
   * URL absoluta del endpoint de autorización.
   *
   * @return URI de autorización
   */
  public String authorizationUri() {
    return server.baseUrl() + AUTHORIZATION_PATH;
  }

  /**
   * URL absoluta del endpoint de intercambio de código por tokens.
   *
   * @return URI del endpoint de tokens
   */
  public String tokenUri() {
    return server.baseUrl() + TOKEN_PATH;
  }

  /**
   * URL absoluta del JWKS del proveedor.
   *
   * @return URI del conjunto de claves públicas
   */
  public String jwksUri() {
    return server.baseUrl() + JWKS_PATH;
  }

  /**
   * URL absoluta del endpoint de información del usuario.
   *
   * @return URI de userinfo
   */
  public String userInfoUri() {
    return server.baseUrl() + USER_INFO_PATH;
  }

  /**
   * Publica el intercambio de código por tokens para el sujeto y el {@code nonce} indicados.
   *
   * @param nonce valor de la petición de autorización que debe transportar el {@code id_token}
   * @param subject identificador del usuario autenticado
   */
  public void stubTokenExchange(String nonce, UUID subject) {
    stubTokenExchange(nonce, subject, TestJwtFactory.token().subject(subject));
  }

  /**
   * Publica el intercambio de código con un {@code id_token} personalizado.
   *
   * <p>La personalización permite reproducir tokens con emisor o sujeto inválidos. El proveedor
   * fija la audiencia, el emisor y el {@code nonce} esperados, y el userinfo devuelve el mismo
   * sujeto para que el flujo continúe hasta la validación del borde.
   *
   * @param nonce valor de la petición de autorización que debe transportar el {@code id_token}
   * @param subject identificador del usuario autenticado
   * @param idTokenFactory fábrica del {@code id_token} con el sujeto y el emisor deseados
   */
  public void stubTokenExchange(String nonce, UUID subject, TestJwtFactory idTokenFactory) {
    String idToken = idTokenFactory.nonce(nonce).audience(CLIENT_ID).build();
    server.stubFor(
        post(urlPathEqualTo(TOKEN_PATH))
            .willReturn(
                okJson(
                    """
                    {"access_token":"%s","token_type":"Bearer","expires_in":300,
                    "id_token":"%s","scope":"openid profile email"}
                    """
                        .formatted(ACCESS_TOKEN, idToken))));
    server.stubFor(
        get(urlPathEqualTo(USER_INFO_PATH))
            .willReturn(
                okJson(
                    """
                    {"sub":"%s","preferred_username":"test-user","name":"Test User"}
                    """
                        .formatted(subject))));
  }
}
