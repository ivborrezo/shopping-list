package dev.ivborrezo.shoppinglist.product.service.product.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Petición de edición parcial de un producto de usuario del catálogo personal.
 *
 * <p>Solo los campos presentes (no {@code null}) se aplican al producto existente. {@code ownerId}
 * es obligatorio como verificación de propiedad: si no coincide con el propietario almacenado, la
 * edición se rechaza. {@code basedOnBaseId} es un campo inmutable de trazabilidad histórica que se
 * ignora si se envía, por lo que el valor almacenado se conserva.
 */
public record UpdateUserProductRequest(
    @NotNull UUID ownerId,
    @Size(max = 128) String name,
    String description,
    Long categoryId,
    Long basedOnBaseId,
    @Size(max = 10) String defaultUnit,
    Integer calories,
    @Size(max = 10) String caloriesPer,
    Boolean shareWithListMembers,
    Boolean shareWithFriends,
    Boolean isActive) {}
