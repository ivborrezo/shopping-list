package dev.ivborrezo.shoppinglist.list.service.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración de la fuente de tiempo del servicio.
 *
 * <p>Expone un {@link Clock} como dependencia inyectable para que los componentes que capturan
 * marcas de instante obtengan la hora actual a través de él.
 */
@Configuration
public class ClockConfig {

  /**
   * Registra el reloj del sistema en UTC como fuente de tiempo inyectable.
   *
   * @return reloj del sistema en UTC
   */
  @Bean
  public Clock clock() {
    return Clock.systemUTC();
  }
}
