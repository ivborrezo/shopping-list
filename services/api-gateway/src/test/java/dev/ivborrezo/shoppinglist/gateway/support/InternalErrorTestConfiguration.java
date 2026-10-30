package dev.ivborrezo.shoppinglist.gateway.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Configuración de test que registra la costura del fallo interno.
 *
 * <p>Cada test de integración que necesita el 500 la importa explícitamente, de modo que el resto
 * de contextos no incorpora el filtro.
 */
@TestConfiguration
public class InternalErrorTestConfiguration {

  /**
   * Registra el filtro que provoca el fallo interno bajo demanda.
   *
   * @return costura del 500
   */
  @Bean
  InternalErrorTriggerFilter internalErrorTriggerFilter() {
    return new InternalErrorTriggerFilter();
  }
}
