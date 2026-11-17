/**
 * Seguridad del borde: validación de identidad, sesiones OAuth2 y trazabilidad del usuario.
 *
 * <p>Reúne las dos cadenas de filtros del gateway, la validación de tokens y del sujeto, el almacén
 * de sesiones acotado y el filtro que publica el identificador del usuario autenticado.
 */
@NullMarked
package dev.ivborrezo.shoppinglist.gateway.security;

import org.jspecify.annotations.NullMarked;
