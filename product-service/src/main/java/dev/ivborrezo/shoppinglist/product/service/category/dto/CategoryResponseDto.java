package dev.ivborrezo.shoppinglist.product.service.category.dto;

import dev.ivborrezo.shoppinglist.product.service.category.entity.Category;

/**
 * DTO de respuesta de una categoría del catálogo, con el nombre ya localizado al idioma resuelto
 * según la cabecera {@code Accept-Language}.
 */
public record CategoryResponseDto(Long id, String code, String name, boolean isActive) {

  /**
   * Construye un DTO de respuesta a partir de la entidad {@link Category}, sin nombre localizado.
   *
   * @param category entidad fuente de la que se copian los campos del DTO
   * @return DTO con los valores de {@code id}, {@code code} e {@code isActive} de la entidad
   * @deprecated usar {@link #from(Category, String)} para incluir el nombre resuelto
   */
  @Deprecated
  public static CategoryResponseDto from(Category category) {
    return new CategoryResponseDto(
        category.getId(), category.getCode(), null, category.getIsActive());
  }

  /**
   * Construye un DTO de respuesta a partir de la entidad {@link Category} y el nombre ya resuelto
   * al idioma solicitado.
   *
   * @param category entidad fuente de la que se copian los campos estructurales del DTO
   * @param name nombre localizado ya resuelto para el idioma de la petición
   * @return DTO con los valores de {@code id}, {@code code}, {@code name} e {@code isActive}
   */
  public static CategoryResponseDto from(Category category, String name) {
    return new CategoryResponseDto(
        category.getId(), category.getCode(), name, category.getIsActive());
  }
}
