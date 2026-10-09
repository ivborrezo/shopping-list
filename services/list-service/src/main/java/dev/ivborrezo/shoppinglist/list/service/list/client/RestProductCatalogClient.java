package dev.ivborrezo.shoppinglist.list.service.list.client;

import dev.ivborrezo.shoppinglist.list.service.common.BusinessException;
import dev.ivborrezo.shoppinglist.list.service.common.ErrorCode;
import dev.ivborrezo.shoppinglist.list.service.common.ProductType;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Adaptador HTTP de {@link ProductCatalogClient} contra {@code product-service}.
 *
 * <p>Traduce los fallos del servicio remoto a los códigos de error del contrato: el producto
 * inexistente o sin nombre es una referencia inválida, y cualquier indisponibilidad del servicio
 * remoto se reporta como servicio no disponible.
 */
@Component
public class RestProductCatalogClient implements ProductCatalogClient {

  private final RestClient productServiceRestClient;

  /**
   * Inyecta por constructor el cliente HTTP del servicio de productos.
   *
   * @param productServiceRestClient cliente HTTP configurado hacia {@code product-service}
   */
  public RestProductCatalogClient(RestClient productServiceRestClient) {
    this.productServiceRestClient = productServiceRestClient;
  }

  /**
   * Consulta a {@code product-service} el nombre del producto referenciado.
   *
   * @param productType tipo del producto (base o de usuario)
   * @param productId identificador del producto en {@code product-service}
   * @return nombre del producto listo para guardarse como snapshot
   * @throws BusinessException con {@link ErrorCode#INVALID_PRODUCT_REFERENCE} si el producto no
   *     existe o no tiene nombre, y {@link ErrorCode#PRODUCT_SERVICE_UNAVAILABLE} si el servicio no
   *     responde
   */
  @Override
  public String resolveDisplayName(ProductType productType, UUID productId) {
    String path =
        switch (productType) {
          case BASE -> "/base-products/" + productId;
          case USER -> "/user-products/" + productId;
        };

    try {
      ProductNameResponse response =
          productServiceRestClient.get().uri(path).retrieve().body(ProductNameResponse.class);

      if (response == null) {
        throw new BusinessException(ErrorCode.INVALID_PRODUCT_REFERENCE);
      }

      String name = response.name();
      if (name == null || name.isBlank()) {
        throw new BusinessException(ErrorCode.INVALID_PRODUCT_REFERENCE);
      }

      return name;
    } catch (RestClientResponseException ex) {
      if (ex.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)) {
        throw new BusinessException(ErrorCode.INVALID_PRODUCT_REFERENCE);
      }
      throw new BusinessException(ErrorCode.PRODUCT_SERVICE_UNAVAILABLE);
    } catch (RestClientException ex) {
      throw new BusinessException(ErrorCode.PRODUCT_SERVICE_UNAVAILABLE);
    }
  }
}
