package dev.ivborrezo.shoppinglist.product.service.product.entity;

import dev.ivborrezo.shoppinglist.product.service.common.CaloriesPerEnum;
import dev.ivborrezo.shoppinglist.product.service.common.UnitEnum;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Producto base del catálogo gestionado por el sistema.
 *
 * <p>Los nombres y descripciones localizados viven en {@code base_product_translation} (patrón i18n
 * Table), relacionados por la colección {@code translations}. La relación con {@code category} se
 * modela como {@code categoryId} (tipo {@code Long}) sin {@code @ManyToOne}, manteniendo bounded
 * contexts separados incluso dentro del mismo servicio.
 *
 * <p>Las columnas {@code default_unit} y {@code calories_per} se almacenan como {@code VARCHAR} y
 * se mapean a los enums de dominio ({@code UnitEnum}, {@code CaloriesPerEnum}) mediante
 * {@code @Enumerated(EnumType.STRING)}.
 */
@Entity
@Table(name = "base_product")
@EntityListeners(AuditingEntityListener.class)
public class BaseProduct {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 64)
  private String code;

  @Column(nullable = false)
  private Long categoryId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private UnitEnum defaultUnit;

  @Column private Integer calories;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private CaloriesPerEnum caloriesPer;

  @Column(nullable = false)
  private Boolean isActive;

  @CreatedDate
  @Column(nullable = false, updatable = false)
  private Instant createdAt;

  @LastModifiedDate
  @Column(nullable = false)
  private Instant updatedAt;

  @OneToMany(
      mappedBy = "baseProduct",
      cascade = {CascadeType.PERSIST, CascadeType.MERGE})
  private Set<BaseProductTranslation> translations = new LinkedHashSet<>();

  /** Constructor sin argumentos exigido por JPA. */
  public BaseProduct() {}

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getCode() {
    return code;
  }

  public void setCode(String code) {
    this.code = code;
  }

  public Long getCategoryId() {
    return categoryId;
  }

  public void setCategoryId(Long categoryId) {
    this.categoryId = categoryId;
  }

  public UnitEnum getDefaultUnit() {
    return defaultUnit;
  }

  public void setDefaultUnit(UnitEnum defaultUnit) {
    this.defaultUnit = defaultUnit;
  }

  public Integer getCalories() {
    return calories;
  }

  public void setCalories(Integer calories) {
    this.calories = calories;
  }

  public CaloriesPerEnum getCaloriesPer() {
    return caloriesPer;
  }

  public void setCaloriesPer(CaloriesPerEnum caloriesPer) {
    this.caloriesPer = caloriesPer;
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

  public Set<BaseProductTranslation> getTranslations() {
    return translations;
  }

  public void setTranslations(Set<BaseProductTranslation> translations) {
    this.translations = translations;
  }
}
