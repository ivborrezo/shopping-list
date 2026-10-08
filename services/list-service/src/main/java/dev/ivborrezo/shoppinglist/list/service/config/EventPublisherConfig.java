package dev.ivborrezo.shoppinglist.list.service.config;

import dev.ivborrezo.shoppinglist.list.service.common.event.DomainEventPublisher;
import dev.ivborrezo.shoppinglist.list.service.common.event.LoggingDomainEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

/**
 * Configuración del adaptador de publicación de eventos de dominio.
 *
 * <p>Es el punto único de registro del puerto {@link DomainEventPublisher}. Al sustituir el
 * adaptador provisional de log por el adaptador del broker real, el cambio se hace aquí.
 */
@Configuration
public class EventPublisherConfig {

  /**
   * Registra el adaptador provisional de publicación de eventos.
   *
   * @param objectMapper mapper Jackson para serializar el evento en el log
   * @return adaptador de publicación basado en log
   */
  @Bean
  public DomainEventPublisher domainEventPublisher(ObjectMapper objectMapper) {
    return new LoggingDomainEventPublisher(objectMapper);
  }
}
