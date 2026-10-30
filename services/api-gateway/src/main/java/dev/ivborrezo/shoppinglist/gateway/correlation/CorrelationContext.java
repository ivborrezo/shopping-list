package dev.ivborrezo.shoppinglist.gateway.correlation;

import io.micrometer.context.ContextRegistry;
import io.micrometer.context.ThreadLocalAccessor;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jspecify.annotations.Nullable;
import org.slf4j.MDC;
import reactor.util.context.Context;
import reactor.util.context.ContextView;

/**
 * Clave y utilidades del contexto reactivo para el identificador de correlación.
 *
 * <p>El {@code Reactor Context} transporta el valor normalizado entre operadores; el puente a
 * {@code MDC} se registra sobre esta misma clave.
 */
public final class CorrelationContext {

  /** Cabecera HTTP de correlación. */
  public static final String HEADER_NAME = "X-Correlation-Id";

  /** Clave del {@code Reactor Context} y del {@code MDC}. */
  public static final String CONTEXT_KEY = "correlationId";

  private static final AtomicBoolean MDC_ACCESSOR_REGISTERED = new AtomicBoolean();

  private CorrelationContext() {}

  /**
   * Registra el puente que renderiza el identificador del contexto reactivo en {@code MDC}.
   *
   * <p>La operación es idempotente: solo el primer registro añade el adaptador al registro global
   * de {@code context-propagation}.
   */
  public static void registerMdcAccessor() {
    if (MDC_ACCESSOR_REGISTERED.compareAndSet(false, true)) {
      ContextRegistry.getInstance().registerThreadLocalAccessor(new MdcThreadLocalAccessor());
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

  private static final class MdcThreadLocalAccessor implements ThreadLocalAccessor<String> {

    @Override
    public Object key() {
      return CONTEXT_KEY;
    }

    @Override
    public @Nullable String getValue() {
      return MDC.get(CONTEXT_KEY);
    }

    @Override
    public void setValue(String value) {
      MDC.put(CONTEXT_KEY, value);
    }

    @Override
    public void setValue() {
      MDC.remove(CONTEXT_KEY);
    }
  }
}
