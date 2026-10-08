package dev.ivborrezo.shoppinglist.list.service.list.entity;

import dev.ivborrezo.shoppinglist.list.service.common.ProductType;
import dev.ivborrezo.shoppinglist.list.service.common.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Ítem de una lista de la compra.
 *
 * <p>Referencia el producto de {@code product-service} de forma polimórfica por {@code
 * (productType, productId)} sin clave foránea física (database-per-service); {@code productId} es
 * el {@code public_id} UUID del producto. {@code listId} es el {@code id} interno de la lista,
 * modelado como {@code Long} sin relación JPA. {@code displayName} es el snapshot congelado del
 * nombre del producto en el momento del alta.
 *
 * <p>{@code productType} se almacena como {@code VARCHAR} y se mapea al enum de dominio {@link
 * ProductType} mediante {@link jakarta.persistence.Enumerated}. Las columnas {@code created_at}/
 * {@code updated_at} se mapean a {@link Instant}.
 */
@Entity
@Table(name = "list_item")
@EntityListeners(AuditingEntityListener.class)
public class ListItem {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "public_id", nullable = false, updatable = false)
  private @Nullable UUID publicId;

  @Column(nullable = false)
  private Long listId;

  @Column(nullable = false)
  private UUID productId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 4)
  private ProductType productType;

  @Column(nullable = false, length = 128)
  private String displayName;

  @Column(nullable = false)
  private Boolean purchased;

  @CreatedDate
  @Column(nullable = false, updatable = false)
  private Instant createdAt;

  @LastModifiedDate
  @Column(nullable = false)
  private Instant updatedAt;

  /** Constructor sin argumentos exigido por JPA. */
  public ListItem() {}

  @PrePersist
  void assignPublicId() {
    if (publicId == null) {
      publicId = UuidV7.generate();
    }
  }

  public @Nullable Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public @Nullable UUID getPublicId() {
    return publicId;
  }

  public void setPublicId(UUID publicId) {
    this.publicId = publicId;
  }

  public Long getListId() {
    return listId;
  }

  public void setListId(Long listId) {
    this.listId = listId;
  }

  public UUID getProductId() {
    return productId;
  }

  public void setProductId(UUID productId) {
    this.productId = productId;
  }

  public ProductType getProductType() {
    return productType;
  }

  public void setProductType(ProductType productType) {
    this.productType = productType;
  }

  public String getDisplayName() {
    return displayName;
  }

  public void setDisplayName(String displayName) {
    this.displayName = displayName;
  }

  public Boolean getPurchased() {
    return purchased;
  }

  public void setPurchased(Boolean purchased) {
    this.purchased = purchased;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
