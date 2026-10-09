package dev.ivborrezo.shoppinglist.list.service.list.event;

import dev.ivborrezo.shoppinglist.list.service.common.ProductType;
import java.util.UUID;

/**
 * Carga del evento de dominio {@code list.item.purchased}.
 *
 * <p>Los identificadores {@code listId}, {@code itemId} y {@code productId} son {@code public_id}
 * externos (UUID); el id interno de base de datos nunca cruza este contrato.
 *
 * @param listId identificador público de la lista a la que pertenece el ítem
 * @param itemId identificador público del ítem marcado o desmarcado como comprado
 * @param productType tipo del producto referenciado
 * @param productId identificador público del producto referenciado
 * @param displayName snapshot del nombre del producto almacenado en el ítem
 * @param purchased {@code true} si el ítem queda marcado como comprado, {@code false} si se
 *     desmarca
 */
public record ListItemPurchasedEvent(
    UUID listId,
    UUID itemId,
    ProductType productType,
    UUID productId,
    String displayName,
    boolean purchased) {}
