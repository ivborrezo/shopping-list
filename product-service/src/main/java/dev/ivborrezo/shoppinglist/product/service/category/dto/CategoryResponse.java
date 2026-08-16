package dev.ivborrezo.shoppinglist.product.service.category.dto;

import dev.ivborrezo.shoppinglist.product.service.category.entity.Category;

/**
 * Respuesta de una categoría del catálogo, con el nombre ya localizado al idioma resuelto según la
 * cabecera {@code Accept-Language}.
 */
public record CategoryResponse(Long id, String code, String name, boolean isActive) {

  /**
   * Construye una respuesta a partir de la entidad {@link Category} y el nombre ya resuelto al
   * idioma solicitado.
   *
   * @param category entidad fuente de la que se copian los campos estructurales de la respuesta
   * @param name nombre localizado ya resuelto para el idioma de la petición
   * @return respuesta con los valores de {@code id}, {@code code}, {@code name} e {@code isActive}
   */
  public static CategoryResponse from(Category category, String name) {
    return new CategoryResponse(category.getId(), category.getCode(), name, category.getIsActive());
  }
}
