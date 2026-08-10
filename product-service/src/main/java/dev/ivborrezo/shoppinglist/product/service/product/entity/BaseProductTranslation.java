package dev.ivborrezo.shoppinglist.product.service.product.entity;

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
 * Traducción localizada del nombre y la descripción de un {@link BaseProduct}.
 *
 * <p>Materializa el patrón i18n Table: una fila por idioma soportado de cada producto base,
 * identificada por la clave compuesta ({@code productId}, {@code locale}). La relación con {@link
 * BaseProduct} se declara con {@link MapsId} para que el identificador de producto de la clave
 * compuesta se derive del propio campo identidad del producto base.
 */
@Entity
@Table(name = "base_product_translation")
@IdClass(BaseProductTranslationId.class)
public class BaseProductTranslation {

  @Id
  @Column(name = "product_id")
  private Long productId;

  @Id
  @Column(length = 5)
  private String locale;

  @Column(nullable = false, length = 128)
  private String name;

  @Column(columnDefinition = "TEXT")
  private String description;

  @MapsId("productId")
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "product_id")
  private BaseProduct baseProduct;

  /** Constructor sin argumentos exigido por JPA. */
  public BaseProductTranslation() {}

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

  public BaseProduct getBaseProduct() {
    return baseProduct;
  }

  public void setBaseProduct(BaseProduct baseProduct) {
    this.baseProduct = baseProduct;
  }
}
