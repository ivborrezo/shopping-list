package dev.ivborrezo.shoppinglist.list.service.list.event;

import java.util.UUID;

/**
 * Carga del evento de dominio {@code list.renamed}.
 *
 * @param listId identificador público de la lista renombrada
 * @param ownerId identificador del propietario de la lista
 * @param oldName nombre que tenía la lista antes del renombrado
 * @param newName nombre con el que quedó la lista tras el renombrado
 */
public record ListRenamedEvent(UUID listId, UUID ownerId, String oldName, String newName) {}
