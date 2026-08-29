package dev.ivborrezo.shoppinglist.product.service.product.entity;

import dev.ivborrezo.shoppinglist.product.service.common.ProductType;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Clave primaria compuesta de {@link UserRecentProduct}.
 *
 * <p>Compuesta por el usuario, el identificador de producto y su tipo. Debe ser {@link
 * Serializable} para servir como {@code @IdClass} de JPA y sobreescribir {@code equals}/{@code
 * hashCode} sobre los tres campos para que la comparación de identidad sea estable entre sesiones
 * de persistencia.
 */
public class UserRecentProductId implements Serializable {

  private UUID userId;

  private Long productId;

  private ProductType productType;

  /** Constructor sin argumentos exigido por JPA para las clases de clave primaria. */
  public UserRecentProductId() {}

  public UUID getUserId() {
    return userId;
  }

  public void setUserId(UUID userId) {
    this.userId = userId;
  }

  public Long getProductId() {
    return productId;
  }

  public void setProductId(Long productId) {
    this.productId = productId;
  }

  public ProductType getProductType() {
    return productType;
  }

  public void setProductType(ProductType productType) {
    this.productType = productType;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof UserRecentProductId that)) {
      return false;
    }
    return Objects.equals(userId, that.userId)
        && Objects.equals(productId, that.productId)
        && productType == that.productType;
  }

  @Override
  public int hashCode() {
    return Objects.hash(userId, productId, productType);
  }
}
