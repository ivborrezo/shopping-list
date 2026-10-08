package dev.ivborrezo.shoppinglist.list.service.common.event;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Catálogo de tipos de evento de dominio del servicio.
 *
 * <p>Centraliza los valores de {@code eventType} según la convención de nombrado de ADR-005,
 * serializados como su valor punteado. En el código se referencia siempre la constante, nunca el
 * literal.
 */
public enum EventType {
  @JsonProperty("list.created")
  LIST_CREATED,
  @JsonProperty("list.renamed")
  LIST_RENAMED,
  @JsonProperty("list.deleted")
  LIST_DELETED,
  @JsonProperty("list.item.added")
  LIST_ITEM_ADDED,
  @JsonProperty("list.item.removed")
  LIST_ITEM_REMOVED,
  @JsonProperty("list.item.purchased")
  LIST_ITEM_PURCHASED
}
