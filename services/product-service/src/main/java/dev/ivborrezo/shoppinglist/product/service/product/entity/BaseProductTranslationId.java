package dev.ivborrezo.shoppinglist.product.service.product.entity;

import java.io.Serializable;
import java.util.Objects;

/**
 * Clave primaria compuesta de {@link BaseProductTranslation}.
 *
 * <p>Compuesta por el identificador del producto base y el locale de la traducción. Debe ser {@link
 * Serializable} para servir como {@code @IdClass} de JPA y sobreescribir {@code equals}/{@code
 * hashCode} sobre ambos campos para que la comparación de identidad sea estable entre sesiones de
 * persistencia.
 */
public class BaseProductTranslationId implements Serializable {

  private Long productId;

  private String locale;

  /** Constructor sin argumentos exigido por JPA para las clases de clave primaria. */
  public BaseProductTranslationId() {}

  public Long getProductId() {
    return productId;
  }

  public void setProductId(Long productId) {
    this.productId = productId;
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
    if (!(o instanceof BaseProductTranslationId that)) {
      return false;
    }
    return Objects.equals(productId, that.productId) && Objects.equals(locale, that.locale);
  }

  @Override
  public int hashCode() {
    return Objects.hash(productId, locale);
  }
}
