package dev.ivborrezo.shoppinglist.product.service.category.dto;

import dev.ivborrezo.shoppinglist.product.service.category.entity.Category;

/** DTO de respuesta de una categoría del catálogo. */
public record CategoryResponseDto(Long id, String code, boolean isActive) {

  /**
   * Construye un DTO de respuesta a partir de la entidad {@link Category}.
   *
   * @param category entidad fuente de la que se copian los campos del DTO
   * @return DTO con los valores de {@code id}, {@code code} e {@code isActive} de la entidad
   */
  public static CategoryResponseDto from(Category category) {
    return new CategoryResponseDto(category.getId(), category.getCode(), category.getIsActive());
  }
}
