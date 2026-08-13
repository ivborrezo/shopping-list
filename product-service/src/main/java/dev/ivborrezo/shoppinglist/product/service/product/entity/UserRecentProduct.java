package dev.ivborrezo.shoppinglist.product.service.product.entity;

import dev.ivborrezo.shoppinglist.product.service.common.ProductType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Relación usuario-producto que registra la última interacción con un producto.
 *
 * <p>Referencia polimórfica a producto ({@code productId}, {@code productType}) sin FK física: la
 * integridad se valida en la capa de aplicación (ADR-013). El timestamp {@code lastUsedAt} lo
 * escribe la capa de aplicación con {@code Instant.now()} al marcar una interacción.
 */
@Entity
@Table(name = "user_recent_product")
@IdClass(UserRecentProductId.class)
public class UserRecentProduct {

  @Id
  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Id
  @Column(name = "product_id", nullable = false)
  private Long productId;

  @Id
  @Enumerated(EnumType.STRING)
  @Column(name = "product_type", nullable = false, length = 4)
  private ProductType productType;

  @Column(name = "last_used_at", nullable = false)
  private Instant lastUsedAt;

  /** Constructor sin argumentos exigido por JPA. */
  public UserRecentProduct() {}

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

  public Instant getLastUsedAt() {
    return lastUsedAt;
  }

  public void setLastUsedAt(Instant lastUsedAt) {
    this.lastUsedAt = lastUsedAt;
  }
}
