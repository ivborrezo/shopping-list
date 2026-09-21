/**
 * Paquete de productos.
 *
 * <p>Abarca productos base ({@code base_product}, con i18n) y productos de usuario ({@code
 * user_product}, texto libre), unificados bajo el tag {@code products} del contrato de API mediante
 * el discriminante {@code type}.
 *
 * <p>Incluye también las relaciones usuario-producto de favoritos ({@code user_favorite_product}) y
 * recientes ({@code user_recent_product}), que referencian polimórficamente a un producto mediante
 * la pareja ({@code productId}, {@code productType}) sin FK física: la integridad se valida en la
 * capa de aplicación (ADR-013).
 *
 * <p>Convención de subcapas: las subcapas por capa de este feature ({@code entity/}, {@code
 * repository/}, {@code service/}, {@code dto/}, {@code controller/}) llevan su propio {@code
 * package-info.java} marcado con {@code @NullMarked}; la anotación no se propaga a los subpaquetes.
 */
@NullMarked
package dev.ivborrezo.shoppinglist.product.service.product;

import org.jspecify.annotations.NullMarked;
