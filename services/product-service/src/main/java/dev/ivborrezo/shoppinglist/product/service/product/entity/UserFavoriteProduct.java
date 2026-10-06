package dev.ivborrezo.shoppinglist.product.service.product.entity;

import dev.ivborrezo.shoppinglist.product.service.common.ProductType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Relación usuario-producto que marca un producto como favorito del usuario.
 *
 * <p>Referencia polimórfica al producto por su identificador externo ({@code productPublicId},
 * {@code productType}) sin FK física: la integridad se valida en la capa de aplicación (ADR-013).
 * La fila lleva una PK surrogate interna ({@code id}), no expuesta, y una clave natural única
 * {@code (userId, productType, productPublicId)}. El timestamp {@code createdAt} lo escribe la capa
 * de aplicación con {@code Instant.now()} al insertar.
 */
@Entity
@Table(name = "user_favorite_product")
public class UserFavoriteProduct {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Enumerated(EnumType.STRING)
  @Column(name = "product_type", nullable = false, length = 4)
  private ProductType productType;

  @Column(name = "product_public_id", nullable = false)
  private UUID productPublicId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  /** Constructor sin argumentos exigido por JPA. */
  public UserFavoriteProduct() {}

  public @Nullable Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public UUID getUserId() {
    return userId;
  }

  public void setUserId(UUID userId) {
    this.userId = userId;
  }

  public ProductType getProductType() {
    return productType;
  }

  public void setProductType(ProductType productType) {
    this.productType = productType;
  }

  public UUID getProductPublicId() {
    return productPublicId;
  }

  public void setProductPublicId(UUID productPublicId) {
    this.productPublicId = productPublicId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }
}
