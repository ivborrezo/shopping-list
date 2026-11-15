package dev.ivborrezo.shoppinglist.testsupport;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

/**
 * Servidor WireMock que publica el JWKS del par RSA de test.
 *
 * <p>Sirve el endpoint de certificados del realm de prueba en un puerto dinámico para que el {@code
 * JwtDecoder} real del servicio pueda descargar las claves al validar un token, sin necesidad de
 * levantar un proveedor OIDC completo.
 */
public final class TestJwksServer {

  private static final String JWKS_PATH =
      "/realms/shopping-list-test/protocol/openid-connect/certs";

  private final WireMockServer server;

  private TestJwksServer(WireMockServer server) {
    this.server = server;
  }

  /**
   * Arranca el servidor y publica el JWKS con la clave pública principal.
   *
   * @return servidor arrancado en un puerto dinámico
   */
  public static TestJwksServer start() {
    WireMockServer server = new WireMockServer(WireMockConfiguration.options().dynamicPort());
    server.start();
    server.stubFor(
        get(JWKS_PATH)
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(TestRsaKey.primary().toJwks())));
    return new TestJwksServer(server);
  }

  /**
   * Devuelve la URL absoluta del JWKS servido.
   *
   * @return URL del endpoint de certificados
   */
  public String jwksUri() {
    return server.baseUrl() + JWKS_PATH;
  }

  /** Detiene el servidor WireMock. */
  public void stop() {
    server.stop();
  }
}
