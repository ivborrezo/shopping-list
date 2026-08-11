package dev.ivborrezo.shoppinglist.product.service.product.dto;

import dev.ivborrezo.shoppinglist.product.service.common.CaloriesPerEnum;
import dev.ivborrezo.shoppinglist.product.service.common.UnitEnum;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Petición de creación de un producto de usuario del catálogo personal.
 *
 * <p>Los campos se validan con Bean Validation en la capa de controller vía {@code @Valid}. {@code
 * name}, {@code defaultUnit} y {@code caloriesPer} son obligatorios salvo que se indique {@code
 * basedOnBaseId}, en cuyo caso se copian del producto base en el locale de la petición; la
 * comprobación de esa regla condicional se realiza en la capa de servicio. {@code basedOnBaseId}
 * queda en el producto creado como trazabilidad inmutable de su origen. {@code defaultUnit} y
 * {@code caloriesPer} se tipan con los enums de dominio ({@link UnitEnum}, {@link
 * CaloriesPerEnum}), de modo que Jackson rechaza con 400 los valores inválidos.
 */
public record CreateUserProductRequest(
    @NotNull UUID ownerId,
    @Size(max = 128) String name,
    String description,
    Long categoryId,
    Long basedOnBaseId,
    UnitEnum defaultUnit,
    Integer calories,
    CaloriesPerEnum caloriesPer,
    Boolean shareWithListMembers,
    Boolean shareWithFriends) {}
