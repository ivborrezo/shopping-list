package dev.ivborrezo.shoppinglist.product.service.product.dto;

import dev.ivborrezo.shoppinglist.product.service.common.CaloriesPerEnum;
import dev.ivborrezo.shoppinglist.product.service.common.UnitEnum;
import dev.ivborrezo.shoppinglist.product.service.product.entity.UserProduct;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Respuesta de un producto de usuario.
 *
 * <p>El contenido ({@code name} y {@code description}) es texto libre monolingüe, sin localización.
 * Los campos {@code defaultUnit} y {@code caloriesPer} se exponen como enums de dominio ({@link
 * UnitEnum}, {@link CaloriesPerEnum}) mapeados desde la columna de base de datos en la capa de
 * respuesta.
 */
public record UserProductResponse(
    @Nullable UUID id,
    UUID ownerId,
    String name,
    @Nullable String description,
    @Nullable UUID categoryId,
    @Nullable UUID basedOnBaseId,
    UnitEnum defaultUnit,
    @Nullable Integer calories,
    CaloriesPerEnum caloriesPer,
    Boolean shareWithListMembers,
    Boolean shareWithFriends,
    Boolean isActive) {

  /**
   * Construye una respuesta a partir de la entidad {@link UserProduct}.
   *
   * @param product entidad fuente de la que se copian los campos de la respuesta
   * @param categoryId identificador público de la categoría a la que pertenece el producto; puede
   *     ser {@code null}
   * @param basedOnBaseId identificador público del producto base del que deriva; puede ser {@code
   *     null}
   * @return respuesta con los valores del producto de usuario, con {@code defaultUnit} y {@code
   *     caloriesPer} mapeados a los enums de dominio
   */
  public static UserProductResponse from(
      UserProduct product, @Nullable UUID categoryId, @Nullable UUID basedOnBaseId) {
    return new UserProductResponse(
        product.getPublicId(),
        product.getOwnerId(),
        product.getName(),
        product.getDescription(),
        categoryId,
        basedOnBaseId,
        product.getDefaultUnit(),
        product.getCalories(),
        product.getCaloriesPer(),
        product.getShareWithListMembers(),
        product.getShareWithFriends(),
        product.getIsActive());
  }
}
