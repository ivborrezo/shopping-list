package dev.ivborrezo.shoppinglist.product.service.product.dto;

import dev.ivborrezo.shoppinglist.product.service.common.ProductType;
import dev.ivborrezo.shoppinglist.product.service.product.entity.UserFavoriteProduct;
import dev.ivborrezo.shoppinglist.product.service.product.entity.UserRecentProduct;

/**
 * DTO de referencia a un producto del catálogo, con el nombre mostrable resuelto.
 *
 * <p>Identifica un producto por su {@code productId} y su {@code productType} (BASE o USER) y
 * adjunta el nombre resuelto: localizado según {@code Accept-Language} para los productos base,
 * monolingüe para los de usuario. {@code name} puede ser {@code null} si el producto referenciado
 * ya no existe (la fila de favorito o reciente se conserva).
 */
public record ProductReferenceDto(Long productId, ProductType productType, String name) {

  /**
   * Construye un DTO de referencia a partir de una entidad de favorito y el nombre resuelto del
   * producto.
   *
   * @param favorite entidad de favorito de la que se copia la referencia al producto
   * @param name nombre mostrable del producto; {@code null} si el producto ya no existe
   * @return DTO con la referencia y el nombre indicado
   */
  public static ProductReferenceDto from(UserFavoriteProduct favorite, String name) {
    return new ProductReferenceDto(favorite.getProductId(), favorite.getProductType(), name);
  }

  /**
   * Construye un DTO de referencia a partir de una entidad de reciente y el nombre resuelto del
   * producto.
   *
   * @param recent entidad de reciente de la que se copia la referencia al producto
   * @param name nombre mostrable del producto; {@code null} si el producto ya no existe
   * @return DTO con la referencia y el nombre indicado
   */
  public static ProductReferenceDto from(UserRecentProduct recent, String name) {
    return new ProductReferenceDto(recent.getProductId(), recent.getProductType(), name);
  }
}
