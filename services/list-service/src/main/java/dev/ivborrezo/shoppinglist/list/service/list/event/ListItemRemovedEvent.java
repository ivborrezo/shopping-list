package dev.ivborrezo.shoppinglist.list.service.list.event;

import dev.ivborrezo.shoppinglist.list.service.common.ProductType;
import java.util.UUID;

/**
 * Carga del evento de dominio {@code list.item.removed}.
 *
 * <p>Los identificadores {@code listId}, {@code itemId} y {@code productId} son {@code public_id}
 * externos (UUID); el id interno de base de datos nunca cruza este contrato.
 *
 * @param listId identificador público de la lista de la que se eliminó el ítem
 * @param itemId identificador público del ítem eliminado
 * @param productType tipo del producto referenciado
 * @param productId identificador público del producto referenciado
 * @param displayName snapshot del nombre que tenía el producto al eliminarse
 */
public record ListItemRemovedEvent(
    UUID listId, UUID itemId, ProductType productType, UUID productId, String displayName) {}
