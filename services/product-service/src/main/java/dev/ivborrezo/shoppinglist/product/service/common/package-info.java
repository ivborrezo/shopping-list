/**
 * Componentes transversales compartidos entre paquetes de dominio.
 *
 * <p>Alberga:
 *
 * <ul>
 *   <li>Enums de dominio ({@code UnitEnum}, {@code CaloriesPerEnum}, {@code ProductType}) usados
 *       por productos base, de usuario y las relaciones usuario-producto.
 *   <li>El DTO de paginación genérico {@code PagedResponse} que envuelve {@code Page<T>} de Spring
 *       Data.
 *   <li>El catálogo de errores del contrato ({@code ErrorCode}), la excepción de negocio ({@code
 *       BusinessException}) y el manejador global de excepciones ({@code GlobalExceptionHandler}).
 * </ul>
 */
package dev.ivborrezo.shoppinglist.product.service.common;
