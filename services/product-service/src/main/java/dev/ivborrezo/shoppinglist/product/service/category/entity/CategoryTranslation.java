package dev.ivborrezo.shoppinglist.product.service.category.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

/**
 * Traducción localizada del nombre de una {@link Category}.
 *
 * <p>Materializa el patrón i18n Table: una fila por idioma soportado de cada categoría,
 * identificada por la clave compuesta ({@code categoryId}, {@code locale}). La relación con {@link
 * Category} se declara con {@link MapsId} para que el identificador de categoría de la clave
 * compuesta se derive del propio campo identidad de la categoría, sin estado redundante duplicado.
 */
@Entity
@Table(name = "category_translation")
@IdClass(CategoryTranslationId.class)
public class CategoryTranslation {

  @Id
  @Column(name = "category_id")
  private Long categoryId;

  @Id
  @Column(length = 5)
  private String locale;

  @Column(nullable = false, length = 128)
  private String name;

  @MapsId("categoryId")
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "category_id")
  private Category category;

  /** Constructor sin argumentos exigido por JPA. */
  public CategoryTranslation() {}

  public Long getCategoryId() {
    return categoryId;
  }

  public void setCategoryId(Long categoryId) {
    this.categoryId = categoryId;
  }

  public String getLocale() {
    return locale;
  }

  public void setLocale(String locale) {
    this.locale = locale;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public Category getCategory() {
    return category;
  }

  public void setCategory(Category category) {
    this.category = category;
  }
}
