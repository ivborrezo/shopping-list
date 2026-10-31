package dev.ivborrezo.shoppinglist.gateway.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Reloj de test cuyo instante avanza de forma explícita.
 *
 * <p>Sustituye al reloj del sistema en las pruebas del limitador para que la recarga de tokens sea
 * determinista, sin esperas reales.
 */
public final class MutableClock extends Clock {

  private final AtomicReference<Instant> current;

  private final ZoneId zone;

  /**
   * Crea el reloj en el instante indicado.
   *
   * @param start instante inicial
   */
  public MutableClock(Instant start) {
    this(start, ZoneOffset.UTC);
  }

  private MutableClock(Instant start, ZoneId zone) {
    this.current = new AtomicReference<>(start);
    this.zone = zone;
  }

  /**
   * Adelanta el reloj la duración indicada.
   *
   * @param duration tiempo que avanza el reloj
   */
  public void advance(Duration duration) {
    current.updateAndGet(instant -> instant.plus(duration));
  }

  @Override
  public ZoneId getZone() {
    return zone;
  }

  @Override
  public Clock withZone(ZoneId zone) {
    return new MutableClock(current.get(), zone);
  }

  @Override
  public Instant instant() {
    return current.get();
  }
}
