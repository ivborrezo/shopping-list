package dev.ivborrezo.shoppinglist.list.service.list.dto;

import dev.ivborrezo.shoppinglist.list.service.common.ProductType;
import dev.ivborrezo.shoppinglist.list.service.list.entity.ListItem;
import java.util.Objects;
import java.util.UUID;

/**
 * Ítem de una lista tal y como se expone en el contrato público.
 *
 * <p>Solo proyecta los campos visibles del contrato: el identificador público del ítem, la
 * referencia al producto ({@code productId} y {@code productType}), el snapshot del nombre ({@code
 * displayName}) y si ya se ha comprado. No expone el id interno ni la relación con la lista.
 */
public record ListItemResponse(
    UUID id, UUID productId, ProductType productType, String displayName, boolean purchased) {

  /**
   * Construye una respuesta a partir de la entidad {@link ListItem}.
   *
   * @param item entidad fuente de la que se copian los campos visibles del contrato
   * @return respuesta con los valores de {@code id}, {@code productId}, {@code productType}, {@code
   *     displayName} y {@code purchased}
   */
  public static ListItemResponse from(ListItem item) {
    return new ListItemResponse(
        Objects.requireNonNull(item.getPublicId()),
        item.getProductId(),
        item.getProductType(),
        item.getDisplayName(),
        item.getPurchased());
  }
}
