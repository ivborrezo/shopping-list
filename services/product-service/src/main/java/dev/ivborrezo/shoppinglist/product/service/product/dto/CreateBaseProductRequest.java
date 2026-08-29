package dev.ivborrezo.shoppinglist.product.service.product.dto;

import dev.ivborrezo.shoppinglist.product.service.common.CaloriesPerEnum;
import dev.ivborrezo.shoppinglist.product.service.common.UnitEnum;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Petición de creación de un producto base del catálogo con sus traducciones.
 *
 * <p>Los campos se validan con Bean Validation en la capa de controller vía {@code @Valid}. El
 * campo {@code calories} es opcional; el resto de campos top-level son obligatorios. {@code
 * translations} exige al menos un elemento para que el endpoint rechace peticiones sin traducciones
 * con 400. {@code defaultUnit} y {@code caloriesPer} se tipan con los enums de dominio ({@link
 * UnitEnum}, {@link CaloriesPerEnum}), de modo que Jackson rechaza con 400 los valores inválidos.
 */
public record CreateBaseProductRequest(
    @NotBlank String code,
    @NotNull Long categoryId,
    @NotNull UnitEnum defaultUnit,
    Integer calories,
    @NotNull CaloriesPerEnum caloriesPer,
    boolean isActive,
    @NotNull @Size(min = 1) List<@Valid ProductTranslation> translations) {

  /**
   * Traducción de nombre y descripción de un producto base en un idioma concreto, embebida en la
   * petición.
   */
  public record ProductTranslation(
      @NotBlank @Size(min = 2, max = 5) String locale,
      @NotBlank @Size(max = 128) String name,
      String description) {}
}
