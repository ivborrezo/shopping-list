package dev.ivborrezo.shoppinglist.list.service.list.event;

import java.util.UUID;

/**
 * Carga del evento de dominio {@code list.deleted}.
 *
 * @param listId identificador público de la lista eliminada
 * @param ownerId identificador del propietario de la lista
 * @param name nombre que tenía la lista en el momento del borrado
 */
public record ListDeletedEvent(UUID listId, UUID ownerId, String name) {}
