/**
 * Paquete de productos.
 *
 * <p>Abarca productos base ({@code base_product}, con i18n) y productos de usuario ({@code
 * user_product}, texto libre), unificados bajo el tag {@code products} del contrato de API mediante
 * el discriminante {@code type}.
 *
 * <p>Convención de subcapas: las subcapas por capa de este feature ({@code entity/}, {@code
 * repository/}, {@code service/}, {@code dto/}, {@code controller/}) son estructurales y no llevan
 * {@code package-info.java} propio; la documentación de paquete vive a este nivel, en el feature.
 */
package dev.ivborrezo.shoppinglist.product.service.product;
