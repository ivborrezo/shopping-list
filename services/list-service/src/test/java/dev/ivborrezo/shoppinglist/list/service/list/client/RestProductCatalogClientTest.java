package dev.ivborrezo.shoppinglist.list.service.list.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.ivborrezo.shoppinglist.list.service.common.BusinessException;
import dev.ivborrezo.shoppinglist.list.service.common.ErrorCode;
import dev.ivborrezo.shoppinglist.list.service.common.ProductType;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Test unitario de {@link RestProductCatalogClient} con el {@link RestClient} mockeado.
 *
 * <p>Cubre la construcción del path según el tipo de producto y la traducción de los fallos HTTP y
 * de red a los códigos de error del contrato, sin levantar Spring ni abrir conexiones.
 */
@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"rawtypes", "unchecked"})
class RestProductCatalogClientTest {

  private static final UUID PRODUCT_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");

  @Mock private RestClient restClient;

  @Mock private RestClient.RequestHeadersUriSpec uriSpec;

  @Mock private RestClient.ResponseSpec responseSpec;

  private RestProductCatalogClient productCatalogClient;

  @BeforeEach
  void setUp() {
    productCatalogClient = new RestProductCatalogClient(restClient);
  }

  /** Construye el path de producto base y devuelve el nombre resuelto. */
  @Test
  void resolveDisplayName_baseProduct_callsBaseProductPathAndReturnsName() {
    stubResponse(new ProductNameResponse("Leche"));

    String name = productCatalogClient.resolveDisplayName(ProductType.BASE, PRODUCT_ID);

    assertThat(name).isEqualTo("Leche");
    verify(uriSpec).uri("/base-products/" + PRODUCT_ID);
  }

  /** Construye el path de producto de usuario y devuelve el nombre resuelto. */
  @Test
  void resolveDisplayName_userProduct_callsUserProductPathAndReturnsName() {
    stubResponse(new ProductNameResponse("Pan"));

    String name = productCatalogClient.resolveDisplayName(ProductType.USER, PRODUCT_ID);

    assertThat(name).isEqualTo("Pan");
    verify(uriSpec).uri("/user-products/" + PRODUCT_ID);
  }

  /** Traduce el 404 del servicio remoto a una referencia de producto inválida. */
  @Test
  void resolveDisplayName_notFound_throwsInvalidProductReference() {
    stubFailure(new HttpClientErrorException(HttpStatus.NOT_FOUND));

    assertThatThrownBy(() -> productCatalogClient.resolveDisplayName(ProductType.BASE, PRODUCT_ID))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_PRODUCT_REFERENCE));
  }

  /** Traduce el error 5xx del servicio remoto a servicio no disponible. */
  @Test
  void resolveDisplayName_serverError_throwsProductServiceUnavailable() {
    stubFailure(new HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE));

    assertThatThrownBy(() -> productCatalogClient.resolveDisplayName(ProductType.USER, PRODUCT_ID))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PRODUCT_SERVICE_UNAVAILABLE));
  }

  /** Traduce un timeout o fallo de conexión a servicio no disponible. */
  @Test
  void resolveDisplayName_timeout_throwsProductServiceUnavailable() {
    stubFailure(new ResourceAccessException("Read timed out"));

    assertThatThrownBy(() -> productCatalogClient.resolveDisplayName(ProductType.BASE, PRODUCT_ID))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PRODUCT_SERVICE_UNAVAILABLE));
  }

  /** Rechaza una respuesta sin nombre para no guardar un snapshot vacío. */
  @Test
  void resolveDisplayName_missingName_throwsInvalidProductReference() {
    stubResponse(new ProductNameResponse(null));

    assertThatThrownBy(() -> productCatalogClient.resolveDisplayName(ProductType.BASE, PRODUCT_ID))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_PRODUCT_REFERENCE));
  }

  private void stubResponse(ProductNameResponse response) {
    when(restClient.get()).thenReturn(uriSpec);
    when(uriSpec.uri(anyString())).thenReturn(uriSpec);
    when(uriSpec.retrieve()).thenReturn(responseSpec);
    when(responseSpec.body(ProductNameResponse.class)).thenReturn(response);
  }

  private void stubFailure(RuntimeException failure) {
    when(restClient.get()).thenReturn(uriSpec);
    when(uriSpec.uri(anyString())).thenReturn(uriSpec);
    when(uriSpec.retrieve()).thenReturn(responseSpec);
    when(responseSpec.body(ProductNameResponse.class)).thenThrow(failure);
  }
}
