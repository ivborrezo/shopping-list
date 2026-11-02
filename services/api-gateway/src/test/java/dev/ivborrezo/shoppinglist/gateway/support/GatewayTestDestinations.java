package dev.ivborrezo.shoppinglist.gateway.support;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.springframework.test.context.DynamicPropertyRegistry;

/**
 * Dobles de los dos servicios de destino para las pruebas de integración del gateway.
 *
 * <p>Cada instancia arranca dos servidores WireMock en puerto dinámico y registra sus URL base en
 * el contexto de Spring. Las pruebas declaran su propio {@code @DynamicPropertySource} y delegan
 * aquí; JUnit no descubre métodos de configuración en un objeto auxiliar.
 */
public final class GatewayTestDestinations {

  private final WireMockServer productService;
  private final WireMockServer listService;

  private GatewayTestDestinations() {
    productService = new WireMockServer(options().dynamicPort());
    listService = new WireMockServer(options().dynamicPort());
  }

  /**
   * Arranca los dos servidores de destino.
   *
   * @return destinos listos para registrar en el contexto de prueba
   */
  public static GatewayTestDestinations start() {
    GatewayTestDestinations destinations = new GatewayTestDestinations();
    destinations.productService.start();
    destinations.listService.start();
    return destinations;
  }

  /** Para los dos servidores de destino. */
  public void stop() {
    productService.stop();
    listService.stop();
  }

  /**
   * Registra las URL base de los dos destinos en el contexto de prueba.
   *
   * @param registry registro de propiedades dinámicas de Spring
   */
  public void register(DynamicPropertyRegistry registry) {
    registry.add("product-service.client.base-url", this::productServiceBaseUrl);
    registry.add("list-service.client.base-url", this::listServiceBaseUrl);
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

  private String productServiceBaseUrl() {
    return "http://localhost:" + productService.port();
  }

  private String listServiceBaseUrl() {
    return "http://localhost:" + listService.port();
  }
}
