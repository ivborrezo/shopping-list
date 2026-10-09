package dev.ivborrezo.shoppinglist.list.service.list.event;

import dev.ivborrezo.shoppinglist.list.service.common.ProductType;
import java.util.UUID;

/**
 * Carga del evento de dominio {@code list.item.added}.
 *
 * <p>Los identificadores {@code listId}, {@code itemId} y {@code productId} son {@code public_id}
 * externos (UUID); el id interno de base de datos nunca cruza este contrato.
 *
 * @param listId identificador público de la lista a la que se añadió el ítem
 * @param itemId identificador público del ítem creado
 * @param productType tipo del producto referenciado
 * @param productId identificador público del producto referenciado
 * @param displayName snapshot del nombre del producto congelado en el momento del alta
 */
public record ListItemAddedEvent(
    UUID listId, UUID itemId, ProductType productType, UUID productId, String displayName) {}
