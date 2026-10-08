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
   * @param event evento a publicar
   */
  void publish(DomainEvent<?> event);
}
