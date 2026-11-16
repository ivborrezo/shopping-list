package dev.ivborrezo.shoppinglist.gateway.correlation;

import io.micrometer.context.ContextRegistry;
import io.micrometer.context.ThreadLocalAccessor;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jspecify.annotations.Nullable;
import org.slf4j.MDC;
import reactor.util.context.Context;
import reactor.util.context.ContextView;

/**
 * Claves y utilidades del contexto reactivo para la trazabilidad de la petición.
 *
 * <p>El {@code Reactor Context} transporta el identificador de correlación y el identificador del
 * usuario autenticado entre operadores; el puente a {@code MDC} se registra sobre ambas claves.
 */
public final class CorrelationContext {

  /** Cabecera HTTP de correlación. */
  public static final String HEADER_NAME = "X-Correlation-Id";

  /** Clave del {@code Reactor Context} y del {@code MDC} para el identificador de correlación. */
  public static final String CONTEXT_KEY = "correlationId";

  /** Clave del {@code Reactor Context} y del {@code MDC} para el identificador del usuario. */
  public static final String USER_ID_CONTEXT_KEY = "userId";

  /** Atributo de {@code ServerWebExchange} que conserva el identificador del usuario. */
  public static final String USER_ID_EXCHANGE_ATTRIBUTE = "shoppinglist.userId";

  private static final AtomicBoolean MDC_ACCESSOR_REGISTERED = new AtomicBoolean();

  private CorrelationContext() {}

  /**
   * Registra los puentes que renderizan el contexto reactivo en {@code MDC}.
   *
   * <p>La operación es idempotente: solo el primer registro añade los adaptadores al registro
   * global de {@code context-propagation}, uno por cada clave sincronizada.
   */
  public static void registerMdcAccessor() {
    if (MDC_ACCESSOR_REGISTERED.compareAndSet(false, true)) {
      ContextRegistry.getInstance()
          .registerThreadLocalAccessor(new MdcThreadLocalAccessor(CONTEXT_KEY));
      ContextRegistry.getInstance()
          .registerThreadLocalAccessor(new MdcThreadLocalAccessor(USER_ID_CONTEXT_KEY));
    }
  }

  /**
   * Añade el identificador de correlación al contexto reactivo.
   *
   * @param context contexto de Reactor en el que se publica el identificador
   * @param correlationId identificador normalizado de la petición
   * @return contexto con el identificador publicado
   */
  public static Context withCorrelationId(Context context, String correlationId) {
    return context.put(CONTEXT_KEY, correlationId);
  }

  /**
   * Lee el identificador de correlación del contexto reactivo.
   *
   * @param context contexto de Reactor de la petición
   * @return identificador normalizado, o {@code null} si el contexto no lo contiene
   */
  public static @Nullable String correlationId(ContextView context) {
    return context.getOrDefault(CONTEXT_KEY, null);
  }

  /**
   * Añade el identificador del usuario autenticado al contexto reactivo.
   *
   * @param context contexto de Reactor en el que se publica el identificador
   * @param userId identificador del usuario autenticado
   * @return contexto con el identificador publicado
   */
  public static Context withUserId(Context context, String userId) {
    return context.put(USER_ID_CONTEXT_KEY, userId);
  }

  /**
   * Lee el identificador del usuario autenticado del contexto reactivo.
   *
   * @param context contexto de Reactor de la petición
   * @return identificador del usuario, o {@code null} si la petición no está autenticada
   */
  public static @Nullable String userId(ContextView context) {
    return context.getOrDefault(USER_ID_CONTEXT_KEY, null);
  }

  private static final class MdcThreadLocalAccessor implements ThreadLocalAccessor<String> {

    private final String key;

    private MdcThreadLocalAccessor(String key) {
      this.key = key;
    }

    @Override
    public Object key() {
      return key;
    }

    @Override
    public @Nullable String getValue() {
      return MDC.get(key);
    }

    @Override
    public void setValue(String value) {
      MDC.put(key, value);
    }

    @Override
    public void setValue() {
      MDC.remove(key);
    }
  }
}
