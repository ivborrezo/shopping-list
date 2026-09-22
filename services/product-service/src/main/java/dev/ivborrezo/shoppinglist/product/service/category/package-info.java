/**
 * Paquete de categorías del catálogo.
 *
 * <p>Entidades gestionadas por el sistema con soporte multiidioma mediante {@code
 * category_translation} (patrón i18n Table).
 *
 * <p>Convención de subcapas: las subcapas por capa de este feature ({@code entity/}, {@code
 * repository/}, y las que surjan) llevan su propio {@code package-info.java} marcado con
 * {@code @NullMarked}: la anotación no se propaga a los subpaquetes. Misma convención a replicar en
 * {@code list-service} por referencia, sin duplicar este texto.
 */
@NullMarked
package dev.ivborrezo.shoppinglist.product.service.category;

import org.jspecify.annotations.NullMarked;
