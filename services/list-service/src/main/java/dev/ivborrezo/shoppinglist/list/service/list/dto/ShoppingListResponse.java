package dev.ivborrezo.shoppinglist.list.service.list.dto;

import dev.ivborrezo.shoppinglist.list.service.list.entity.ListItem;
import dev.ivborrezo.shoppinglist.list.service.list.entity.ShoppingList;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Detalle de una lista de la compra con sus ítems inline.
 *
 * <p>Es la representación del schema {@code ShoppingList} del contrato: además de la identidad
 * pública, el propietario, el nombre y las marcas de auditoría, incluye la lista de ítems
 * existentes en el orden que devuelve la persistencia. {@code items} nunca es {@code null}; si la
 * lista no tiene ítems se expone una lista vacía.
 */
public record ShoppingListResponse(
    UUID id,
    UUID ownerId,
    String name,
    Instant createdAt,
    Instant updatedAt,
    List<ListItemResponse> items) {

  /**
   * Construye el detalle de una lista a partir de su entidad y los ítems ya cargados.
   *
   * @param list entidad fuente de la que se copian los campos de la lista
   * @param items ítems existentes de la lista, en el orden de presentación
   * @return detalle de la lista con cada ítem mapeado a {@link ListItemResponse}
   */
  public static ShoppingListResponse from(ShoppingList list, List<ListItem> items) {
    return new ShoppingListResponse(
        Objects.requireNonNull(list.getPublicId()),
        list.getOwnerId(),
        list.getName(),
        list.getCreatedAt(),
        list.getUpdatedAt(),
        items.stream().map(ListItemResponse::from).toList());
  }
}
