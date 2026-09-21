package dev.ivborrezo.shoppinglist.product.service.product.dto;

import dev.ivborrezo.shoppinglist.product.service.common.CaloriesPerEnum;
import dev.ivborrezo.shoppinglist.product.service.common.UnitEnum;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Petición de edición parcial de un producto base del catálogo.
 *
 * <p>Todos los campos son opcionales: solo los campos presentes (no {@code null}) se aplican al
 * producto existente. {@code translations}, si está presente, reemplaza el conjunto completo de
 * traducciones del producto. {@code defaultUnit} y {@code caloriesPer} se tipan con los enums de
 * dominio ({@link UnitEnum}, {@link CaloriesPerEnum}).
 */
public record UpdateBaseProductRequest(
    @Nullable String code,
    @Nullable Long categoryId,
    @Nullable UnitEnum defaultUnit,
    @Nullable Integer calories,
    @Nullable CaloriesPerEnum caloriesPer,
    @Nullable Boolean isActive,
    @Nullable List<@Valid ProductTranslation> translations) {

  /** Traducción de nombre y descripción de un producto base, embebida en la petición de edición. */
  public record ProductTranslation(
      @NotBlank @Size(min = 2, max = 5) String locale,
      @NotBlank @Size(max = 128) String name,
      @Nullable String description) {}
}
