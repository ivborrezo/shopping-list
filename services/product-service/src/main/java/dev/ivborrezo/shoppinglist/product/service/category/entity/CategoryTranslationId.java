package dev.ivborrezo.shoppinglist.product.service.category.entity;

import java.io.Serializable;
import java.util.Objects;

/**
 * Clave primaria compuesta de {@link CategoryTranslation}.
 *
 * <p>Compuesta por el identificador de la categoría y el locale de la traducción. Debe ser {@link
 * Serializable} para servir como {@code @IdClass} de JPA y sobreescribir {@code equals}/{@code
 * hashCode} sobre ambos campos para que la comparación de identidad sea estable entre sesiones de
 * persistencia.
 */
public class CategoryTranslationId implements Serializable {

  private Long categoryId;

  private String locale;

  /** Constructor sin argumentos exigido por JPA para las clases de clave primaria. */
  public CategoryTranslationId() {}

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

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof CategoryTranslationId that)) {
      return false;
    }
    return Objects.equals(categoryId, that.categoryId) && Objects.equals(locale, that.locale);
  }

  @Override
  public int hashCode() {
    return Objects.hash(categoryId, locale);
  }
}
