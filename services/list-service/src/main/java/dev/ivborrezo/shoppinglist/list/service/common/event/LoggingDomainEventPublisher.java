package dev.ivborrezo.shoppinglist.list.service.common.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;

/**
 * Adaptador provisional de {@link DomainEventPublisher} que registra el evento en el log.
 *
 * <p>La entrega se difiere a after-commit si hay una transacción activa, de modo que solo se
 * publican hechos ya confirmados. Es el adaptador que se sustituye por el del broker real cuando se
 * resuelva su elección, sin tocar la lógica de negocio.
 */
public class LoggingDomainEventPublisher implements DomainEventPublisher {

  private static final Logger log = LoggerFactory.getLogger(LoggingDomainEventPublisher.class);

  private final ObjectMapper objectMapper;

  /**
   * Construye el adaptador con el mapper que serializa el evento en el log.
   *
   * @param objectMapper mapper Jackson para serializar el envelope del evento
   */
  public LoggingDomainEventPublisher(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  /**
   * Publica el evento, difiriendo la entrega a after-commit si hay una transacción activa.
   *
   * @param event evento a publicar
   */
  @Override
  public void publish(DomainEvent<?> event) {
    if (TransactionSynchronizationManager.isActualTransactionActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              deliver(event);
            }
          });
    } else {
      deliver(event);
    }
  }

  private void deliver(DomainEvent<?> event) {
    try {
      log.info("Domain event published: {}", objectMapper.writeValueAsString(event));
    } catch (Exception ex) {
      log.error("Failed to publish domain event", ex);
    }
  }
}
