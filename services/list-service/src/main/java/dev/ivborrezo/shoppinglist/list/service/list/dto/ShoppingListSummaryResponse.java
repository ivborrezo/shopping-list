package dev.ivborrezo.shoppinglist.list.service.list.dto;

import dev.ivborrezo.shoppinglist.list.service.list.entity.ShoppingList;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Resumen de una lista de la compra sin sus ítems, usado en el listado paginado.
 *
 * <p>Proyecta la identidad pública, el propietario, el nombre y las marcas de auditoría; los ítems
 * solo se incluyen en la vista de detalle.
 */
public record ShoppingListSummaryResponse(
    UUID id, UUID ownerId, String name, Instant createdAt, Instant updatedAt) {

  /**
   * Construye un resumen a partir de la entidad {@link ShoppingList}.
   *
   * @param list entidad fuente de la que se copian los campos del resumen
   * @return resumen con los valores de {@code id}, {@code ownerId}, {@code name}, {@code createdAt}
   *     y {@code updatedAt}
   */
  public static ShoppingListSummaryResponse from(ShoppingList list) {
    return new ShoppingListSummaryResponse(
        Objects.requireNonNull(list.getPublicId()),
        list.getOwnerId(),
        list.getName(),
        list.getCreatedAt(),
        list.getUpdatedAt());
  }
}
