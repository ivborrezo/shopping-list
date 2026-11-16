package dev.ivborrezo.shoppinglist.gateway.support;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

import com.github.tomakehurst.wiremock.WireMockServer;
import dev.ivborrezo.shoppinglist.testsupport.TestUsers;
import org.springframework.test.context.DynamicPropertyRegistry;

/**
 * Dobles de los dos servicios de destino y del proveedor OIDC para las pruebas del gateway.
 *
 * <p>Cada instancia arranca dos servidores WireMock y un proveedor OIDC de test en puerto dinámico,
 * y registra sus URL en el contexto de Spring. El proveedor OIDC sustituye a Keycloak tanto en el
 * JWKS del canal {@code Bearer} como en los endpoints del login por sesión. Las pruebas declaran su
 * propio {@code @DynamicPropertySource} y delegan aquí; JUnit no descubre métodos de configuración
 * en un objeto auxiliar.
 */
public final class GatewayTestDestinations {

  private final WireMockServer productService;

  private final WireMockServer listService;

  private TestOidcProvider oidcProvider;

  private GatewayTestDestinations() {
    productService = new WireMockServer(options().dynamicPort());
    listService = new WireMockServer(options().dynamicPort());
  }

  /**
   * Arranca los dos servidores de destino y el proveedor OIDC de test.
   *
   * @return destinos listos para registrar en el contexto de prueba
   */
  public static GatewayTestDestinations start() {
    GatewayTestDestinations destinations = new GatewayTestDestinations();
    destinations.productService.start();
    destinations.listService.start();
    destinations.oidcProvider = TestOidcProvider.start();
    return destinations;
  }

  /** Para los dos servidores de destino y el proveedor OIDC. */
  public void stop() {
    productService.stop();
    listService.stop();
    oidcProvider.stop();
  }

  /**
   * Registra las URL base de los destinos y los metadatos OIDC en el contexto de prueba.
   *
   * @param registry registro de propiedades dinámicas de Spring
   */
  public void register(DynamicPropertyRegistry registry) {
    registry.add("product-service.client.base-url", this::productServiceBaseUrl);
    registry.add("list-service.client.base-url", this::listServiceBaseUrl);
    registry.add(
        "spring.security.oauth2.client.provider.keycloak.authorization-uri",
        oidcProvider::authorizationUri);
    registry.add(
        "spring.security.oauth2.client.provider.keycloak.token-uri", oidcProvider::tokenUri);
    registry.add(
        "spring.security.oauth2.client.provider.keycloak.jwk-set-uri", oidcProvider::jwksUri);
    registry.add(
        "spring.security.oauth2.client.provider.keycloak.user-info-uri", oidcProvider::userInfoUri);
    registry.add(
        "spring.security.oauth2.client.registration.keycloak.client-id",
        () -> TestOidcProvider.CLIENT_ID);
    registry.add(
        "spring.security.oauth2.client.registration.keycloak.client-secret",
        () -> TestOidcProvider.CLIENT_SECRET);
    registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", oidcProvider::jwksUri);
    registry.add(
        "spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> TestUsers.TEST_ISSUER);
    registry.add("api-gateway.security.issuer-uri", () -> TestUsers.TEST_ISSUER);
  }

  /**
   * Servidor WireMock que representa a product-service.
   *
   * @return servidor de product-service
   */
  public WireMockServer productService() {
    return productService;
  }

  /**
   * Servidor WireMock que representa a list-service.
   *
   * @return servidor de list-service
   */
  public WireMockServer listService() {
    return listService;
  }

  /**
   * Proveedor OIDC de test que sirve el JWKS y el intercambio de tokens.
   *
   * @return proveedor OIDC de test
   */
  public TestOidcProvider oidcProvider() {
    return oidcProvider;
  }

  private String productServiceBaseUrl() {
    return "http://localhost:" + productService.port();
  }

  private String listServiceBaseUrl() {
    return "http://localhost:" + listService.port();
  }
}
