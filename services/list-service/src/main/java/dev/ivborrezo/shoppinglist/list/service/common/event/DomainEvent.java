package dev.ivborrezo.shoppinglist.list.service.common.event;

import java.time.Instant;

/**
 * Envelope de un evento de dominio publicado por el servicio.
 *
 * @param <T> tipo de la carga específica del evento
 * @param eventType tipo del evento, del catálogo {@link EventType}
 * @param correlationId identificador de correlación de la petición que originó el hecho
 * @param occurredAt instante UTC en que ocurrió el hecho
 * @param payload carga específica del evento
 */
public record DomainEvent<T>(
    EventType eventType, String correlationId, Instant occurredAt, T payload) {}
