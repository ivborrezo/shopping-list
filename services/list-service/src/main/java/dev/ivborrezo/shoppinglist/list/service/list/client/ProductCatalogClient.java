package dev.ivborrezo.shoppinglist.list.service.list.client;

import dev.ivborrezo.shoppinglist.list.service.common.BusinessException;
import dev.ivborrezo.shoppinglist.list.service.common.ErrorCode;
import dev.ivborrezo.shoppinglist.list.service.common.ProductType;
import java.util.UUID;

/**
 * Puerto de resolución de productos de {@code product-service}.
 *
 * <p>Desacopla la capa de aplicación del mecanismo HTTP concreto con el que se consulta el catálogo
 * de productos; el adaptador vive en {@link RestProductCatalogClient}.
 */
public interface ProductCatalogClient {

  /**
   * Resuelve el nombre del producto referenciado por un ítem de lista.
   *
   * @param productType tipo del producto (base o de usuario)
   * @param productId identificador del producto en {@code product-service}
   * @return nombre del producto listo para guardarse como snapshot
   * @throws BusinessException con {@link ErrorCode#INVALID_PRODUCT_REFERENCE} si el producto no
   *     existe o no tiene nombre, y {@link ErrorCode#PRODUCT_SERVICE_UNAVAILABLE} si el servicio no
   *     responde
   */
  String resolveDisplayName(ProductType productType, UUID productId);
}
