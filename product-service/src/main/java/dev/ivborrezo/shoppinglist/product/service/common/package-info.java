/**
 * Componentes transversales compartidos entre paquetes de dominio.
 *
 * <p>Alberga:
 *
 * <ul>
 *   <li>Enums de dominio ({@code UnitEnum}, {@code CaloriesPerEnum}) usados por productos base y de
 *       usuario.
 *   <li>El DTO de paginación genérico {@code PagedResponse} que envuelve {@code Page<T>} de Spring
 *       Data.
 *   <li>Destinado también al DTO de error estándar de la API, el manejador global de excepciones
 *       ({@code @ControllerAdvice}) y utilidades comunes.
 * </ul>
 */
package dev.ivborrezo.shoppinglist.product.service.common;
