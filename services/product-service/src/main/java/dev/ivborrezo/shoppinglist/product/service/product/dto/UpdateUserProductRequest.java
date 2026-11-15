package dev.ivborrezo.shoppinglist.product.service.product.dto;

import dev.ivborrezo.shoppinglist.product.service.common.CaloriesPerEnum;
import dev.ivborrezo.shoppinglist.product.service.common.UnitEnum;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Petición de edición parcial de un producto de usuario del catálogo personal.
 *
 * <p>Solo los campos presentes (no {@code null}) se aplican al producto existente. El propietario
 * se obtiene del usuario autenticado y se verifica contra el almacenado, sin formar parte de la
 * petición. {@code basedOnBaseId} es un campo inmutable de trazabilidad histórica que se ignora si
 * se envía, por lo que el valor almacenado se conserva. {@code defaultUnit} y {@code caloriesPer}
 * se tipan con los enums de dominio ({@link UnitEnum}, {@link CaloriesPerEnum}).
 */
public record UpdateUserProductRequest(
    @Size(max = 128) @Nullable String name,
    @Nullable String description,
    @Nullable UUID categoryId,
    @Nullable UUID basedOnBaseId,
    @Nullable UnitEnum defaultUnit,
    @Nullable Integer calories,
    @Nullable CaloriesPerEnum caloriesPer,
    @Nullable Boolean shareWithListMembers,
    @Nullable Boolean shareWithFriends,
    @Nullable Boolean isActive) {}
