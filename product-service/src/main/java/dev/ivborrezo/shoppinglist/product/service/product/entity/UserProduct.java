package dev.ivborrezo.shoppinglist.product.service.product.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Producto creado por un usuario a partir de su catálogo personal.
 *
 * <p>El contenido ({@code name} y {@code description}) es texto libre monolingüe, sin tabla de
 * traducciones. Las relaciones con {@code category} y {@code base_product} se modelan como {@code
 * categoryId} y {@code basedOnBaseId} (tipo {@code Long}) sin relaciones JPA, manteniendo bounded
 * contexts separados. {@code basedOnBaseId} es una traza histórica inmutable del producto base del
 * que deriva el usuario; la FK correspondiente se elimina en cascada a {@code NULL} para no
 * bloquear la vida de este producto.
 *
 * <p>Las columnas {@code default_unit} y {@code calories_per} se almacenan como {@code VARCHAR} y
 * el mapeo a los enums de dominio se realiza en la capa de DTO/API, no en la capa de persistencia.
 */
@Entity
@Table(name = "user_product")
@EntityListeners(AuditingEntityListener.class)
public class UserProduct {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private UUID ownerId;

  @Column(nullable = false, length = 128)
  private String name;

  @Column private String description;

  @Column private Long categoryId;

  @Column private Long basedOnBaseId;

  @Column(nullable = false, length = 10)
  private String defaultUnit;

  @Column private Integer calories;

  @Column(nullable = false, length = 10)
  private String caloriesPer;

  @Column(nullable = false)
  private Boolean shareWithListMembers;

  @Column(nullable = false)
  private Boolean shareWithFriends;

  @Column(nullable = false)
  private Boolean isActive;

  @CreatedDate
  @Column(nullable = false, updatable = false)
  private Instant createdAt;

  @LastModifiedDate
  @Column(nullable = false)
  private Instant updatedAt;

  /** Constructor sin argumentos exigido por JPA. */
  public UserProduct() {}

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
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

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public Long getCategoryId() {
    return categoryId;
  }

  public void setCategoryId(Long categoryId) {
    this.categoryId = categoryId;
  }

  public Long getBasedOnBaseId() {
    return basedOnBaseId;
  }

  public void setBasedOnBaseId(Long basedOnBaseId) {
    this.basedOnBaseId = basedOnBaseId;
  }

  public String getDefaultUnit() {
    return defaultUnit;
  }

  public void setDefaultUnit(String defaultUnit) {
    this.defaultUnit = defaultUnit;
  }

  public Integer getCalories() {
    return calories;
  }

  public void setCalories(Integer calories) {
    this.calories = calories;
  }

  public String getCaloriesPer() {
    return caloriesPer;
  }

  public void setCaloriesPer(String caloriesPer) {
    this.caloriesPer = caloriesPer;
  }

  public Boolean getShareWithListMembers() {
    return shareWithListMembers;
  }

  public void setShareWithListMembers(Boolean shareWithListMembers) {
    this.shareWithListMembers = shareWithListMembers;
  }

  public Boolean getShareWithFriends() {
    return shareWithFriends;
  }

  public void setShareWithFriends(Boolean shareWithFriends) {
    this.shareWithFriends = shareWithFriends;
  }

  public Boolean getIsActive() {
    return isActive;
  }

  public void setIsActive(Boolean isActive) {
    this.isActive = isActive;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
