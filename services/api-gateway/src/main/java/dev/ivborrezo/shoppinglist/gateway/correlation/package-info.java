/**
 * Correlación de peticiones en el borde.
 *
 * <p>Normaliza el identificador {@code X-Correlation-Id} de cada petición y lo publica en el
 * contexto reactivo para que atraviese la cadena de filtros y llegue a los servicios de destino.
 */
@NullMarked
package dev.ivborrezo.shoppinglist.gateway.correlation;

import org.jspecify.annotations.NullMarked;
