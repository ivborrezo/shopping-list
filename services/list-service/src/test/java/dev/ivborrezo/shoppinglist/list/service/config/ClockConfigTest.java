package dev.ivborrezo.shoppinglist.list.service.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/**
 * Tests unitarios de {@link ClockConfig}.
 *
 * <p>Verifican el reloj expuesto como bean sin levantar contexto de Spring.
 */
class ClockConfigTest {

  /**
   * El reloj del bean usa la zona UTC.
   *
   * <p>Comprueba también que la instancia no es nula.
   */
  @Test
  void clock_returnsUtcClock() {
    Clock clock = new ClockConfig().clock();

    assertThat(clock).isNotNull();
    assertThat(clock.getZone()).isEqualTo(ZoneOffset.UTC);
  }

  /**
   * El instante del reloj corresponde al momento actual.
   *
   * <p>Tolera una desviación de cinco segundos respecto a {@link Instant#now()} para no acoplar el
   * test a la precisión del reloj del sistema.
   */
  @Test
  void clock_returnsCurrentInstant() {
    Instant instant = new ClockConfig().clock().instant();

    assertThat(instant).isNotNull();
    assertThat(instant).isBetween(Instant.now().minusSeconds(5), Instant.now().plusSeconds(5));
  }
}
