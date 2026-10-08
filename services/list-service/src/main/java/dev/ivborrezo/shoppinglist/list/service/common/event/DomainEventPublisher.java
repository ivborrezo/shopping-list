package dev.ivborrezo.shoppinglist.list.service.common.event;

/**
 * Puerto de salida para publicar eventos de dominio.
 *
 * <p>El dominio no conoce el broker: invoca esta interfaz y es el adaptador concreto el que
 * materializa la entrega. La implementación difiere la publicación a after-commit cuando hay una
 * transacción activa, para no notificar hechos que finalmente no se confirmen.
 */
public interface DomainEventPublisher {

  /**
   * Publica un evento de dominio.
   *
   * <p>La entrega es best-effort: no garantiza entrega, orden ni idempotencia, y un fallo de
   * publicación no se propaga al cliente.
   *
   * <p>Invariante de correlación: el {@code correlationId} del evento no es nulo. Quien publica
   * debe hacerlo dentro de un contexto de correlación; en peticiones HTTP lo aporta el filtro de
   * correlación (cabecera {@code X-Correlation-Id} o UUID generado), y cualquier entrada no-HTTP
   * (job programado, consumidor de mensajes) debe establecerlo antes de publicar.
   *
   * @param event evento a publicar
   */
  void publish(DomainEvent<?> event);
}
