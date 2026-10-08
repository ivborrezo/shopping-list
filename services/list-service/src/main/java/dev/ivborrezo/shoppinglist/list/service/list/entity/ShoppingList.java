package dev.ivborrezo.shoppinglist.list.service.list.entity;

import dev.ivborrezo.shoppinglist.list.service.common.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
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
 * Lista de la compra, agregado raíz de la feature.
 *
 * <p>El identificador interno {@code id} es la clave primaria subrogada y no se expone; la
 * identidad externa es {@code publicId} (UUID v7, ADR-016), generada por la aplicación en un {@link
 * jakarta.persistence.PrePersist}. Las columnas {@code created_at}/{@code updated_at} se almacenan
 * como {@code TIMESTAMPTZ} en PostgreSQL y se mapean a {@link Instant} (instante absoluto, sin zona
 * adjunta); es la convención transversal del monorepo.
 */
@Entity
@Table(name = "list")
@EntityListeners(AuditingEntityListener.class)
public class ShoppingList {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "public_id", nullable = false, updatable = false)
  private @Nullable UUID publicId;

  @Column(nullable = false)
  private UUID ownerId;

  @Column(nullable = false, length = 128)
  private String name;

  @CreatedDate
  @Column(nullable = false, updatable = false)
  private Instant createdAt;

  @LastModifiedDate
  @Column(nullable = false)
  private Instant updatedAt;

  /** Constructor sin argumentos exigido por JPA. */
  public ShoppingList() {}

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

  public UUID getOwnerId() {
    return ownerId;
  }

  public void setOwnerId(UUID ownerId) {
    this.ownerId = ownerId;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
