package dev.ivborrezo.shoppinglist.product.service.product.dto;

import dev.ivborrezo.shoppinglist.product.service.common.CaloriesPerEnum;
import dev.ivborrezo.shoppinglist.product.service.common.UnitEnum;
import dev.ivborrezo.shoppinglist.product.service.product.entity.BaseProduct;

/**
 * DTO de respuesta de un producto base del catálogo, con el nombre y la descripción ya localizados
 * al idioma resuelto según la cabecera {@code Accept-Language}.
 *
 * <p>Los campos {@code defaultUnit} y {@code caloriesPer} se exponen como enums de dominio ({@link
 * UnitEnum}, {@link CaloriesPerEnum}).
 */
public record BaseProductResponseDto(
    Long id,
    String code,
    Long categoryId,
    UnitEnum defaultUnit,
    Integer calories,
    CaloriesPerEnum caloriesPer,
    Boolean isActive,
    String name,
    String description) {

  /**
   * Construye un DTO de respuesta a partir de la entidad {@link BaseProduct} con el nombre y la
   * descripción ya resueltos al idioma solicitado.
   *
   * @param product entidad fuente de la que se copian los campos estructurales del DTO
   * @param name nombre localizado ya resuelto para el idioma de la petición
   * @param description descripción localizada ya resuelta; puede ser {@code null}
   * @return DTO con los valores estructurales del producto base y los textos localizados
   */
  public static BaseProductResponseDto from(BaseProduct product, String name, String description) {
    return new BaseProductResponseDto(
        product.getId(),
        product.getCode(),
        product.getCategoryId(),
        product.getDefaultUnit(),
        product.getCalories(),
        product.getCaloriesPer(),
        product.getIsActive(),
        name,
        description);
  }
}
