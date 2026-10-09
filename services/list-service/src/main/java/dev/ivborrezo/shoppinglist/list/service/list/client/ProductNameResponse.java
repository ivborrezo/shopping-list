package dev.ivborrezo.shoppinglist.list.service.list.client;

import org.jspecify.annotations.Nullable;

/**
 * Proyección de la respuesta de {@code product-service} con el nombre del producto.
 *
 * <p>Solo declara el campo consumido por el adaptador y descarta el resto del cuerpo. {@code name}
 * es nulable porque el producto puede no tener nombre resuelto en el idioma solicitado.
 *
 * @param name nombre resuelto del producto, o {@code null} si no tiene
 */
public record ProductNameResponse(@Nullable String name) {}
