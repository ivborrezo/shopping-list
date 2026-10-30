/**
 * Contrato de error del borde.
 *
 * <p>El gateway es el único emisor de sus propios errores: un escritor único traduce la excepción a
 * un {@code ProblemDetail} con el catálogo estable de códigos y títulos del borde.
 */
@NullMarked
package dev.ivborrezo.shoppinglist.gateway.error;

import org.jspecify.annotations.NullMarked;
