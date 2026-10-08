package dev.ivborrezo.shoppinglist.list.service.list.event;

import java.util.UUID;

/**
 * Carga del evento de dominio {@code list.created}.
 *
 * @param listId identificador público de la lista creada
 * @param ownerId identificador del propietario de la lista
 * @param name nombre con el que se creó la lista
 */
public record ListCreatedEvent(UUID listId, UUID ownerId, String name) {}
