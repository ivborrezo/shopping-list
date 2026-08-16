package dev.ivborrezo.shoppinglist.product.service.product.dto;

import dev.ivborrezo.shoppinglist.product.service.common.CaloriesPerEnum;
import dev.ivborrezo.shoppinglist.product.service.common.UnitEnum;
import dev.ivborrezo.shoppinglist.product.service.product.entity.UserProduct;
import java.util.UUID;

/**
 * Respuesta de un producto de usuario.
 *
 * <p>El contenido ({@code name} y {@code description}) es texto libre monolingüe, sin localización.
 * Los campos {@code defaultUnit} y {@code caloriesPer} se exponen como enums de dominio ({@link
 * UnitEnum}, {@link CaloriesPerEnum}) mapeados desde la columna de base de datos en la capa de
 * respuesta.
 */
public record UserProductResponse(
    Long id,
    UUID ownerId,
    String name,
    String description,
    Long categoryId,
    Long basedOnBaseId,
    UnitEnum defaultUnit,
    Integer calories,
    CaloriesPerEnum caloriesPer,
    Boolean shareWithListMembers,
    Boolean shareWithFriends,
    Boolean isActive) {

  /**
   * Construye una respuesta a partir de la entidad {@link UserProduct}.
   *
   * @param product entidad fuente de la que se copian los campos de la respuesta
   * @return respuesta con los valores del producto de usuario, con {@code defaultUnit} y {@code
   *     caloriesPer} mapeados a los enums de dominio
   */
  public static UserProductResponse from(UserProduct product) {
    return new UserProductResponse(
        product.getId(),
        product.getOwnerId(),
        product.getName(),
        product.getDescription(),
        product.getCategoryId(),
        product.getBasedOnBaseId(),
        product.getDefaultUnit(),
        product.getCalories(),
        product.getCaloriesPer(),
        product.getShareWithListMembers(),
        product.getShareWithFriends(),
        product.getIsActive());
  }
}
