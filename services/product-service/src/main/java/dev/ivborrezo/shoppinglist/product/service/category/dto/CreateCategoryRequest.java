package dev.ivborrezo.shoppinglist.product.service.category.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Petición de creación de una categoría del catálogo con sus traducciones.
 *
 * <p>Los campos se validan con Bean Validation en la capa de controller vía {@code @Valid}. El
 * campo {@code isActive} es primitivo y tiene valor siempre; {@code translations} exige al menos un
 * elemento para que el endpoint rechace peticiones sin traducciones con 400.
 */
public record CreateCategoryRequest(
    @NotBlank String code,
    boolean isActive,
    @NotNull @Size(min = 1) List<@Valid Translation> translations) {

  /** Traducción de un nombre de categoría en un idioma concreto, embebida en la petición. */
  public record Translation(
      @NotBlank @Size(min = 2, max = 5) String locale, @NotBlank @Size(max = 128) String name) {}
}
