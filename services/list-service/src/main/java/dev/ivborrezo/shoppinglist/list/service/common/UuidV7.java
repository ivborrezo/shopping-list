package dev.ivborrezo.shoppinglist.list.service.common;

import com.github.f4b6a3.uuid.UuidCreator;
import java.util.UUID;

/**
 * Generador de identificadores públicos UUID v7 de la aplicación.
 *
 * <p>Encapsula la librería de generación de UUID: es el único punto del código que conoce {@code
 * uuid-creator}. Los UUID v7 son ordenables por tiempo de creación, lo que favorece la localidad de
 * los índices frente a un UUID v4 aleatorio.
 */
public final class UuidV7 {

  private UuidV7() {}

  /**
   * Genera un identificador público UUID v7.
   *
   * @return nuevo UUID v7
   */
  public static UUID generate() {
    return UuidCreator.getTimeOrderedEpoch();
  }
}
