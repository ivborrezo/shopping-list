package dev.ivborrezo.shoppinglist.list.service.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Filtro canónico de correlación de peticiones del servicio.
 *
 * <p>Reutiliza la cabecera {@code X-Correlation-Id} de la petición entrante cuando ya viene
 * informada (llamadas internas entre servicios) o genera un UUID nuevo para las peticiones que
 * entran desde fuera. En ambos casos publica el valor en el MDC bajo la clave {@code
 * correlationId}, de modo que aparezca automáticamente en todas las líneas de log de la petición, y
 * lo devuelve en la cabecera de respuesta.
 *
 * <p>Se registra con {@link Ordered#HIGHEST_PRECEDENCE} para ser el filtro más externo de la
 * cadena: es el primero en ejecutarse y el último en salir, así cualquier otro filtro o componente
 * que loguee durante el ciclo de vida de la petición ya encuentra el {@code correlationId}
 * disponible en el MDC.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

  private static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

  private static final String MDC_KEY = "correlationId";

  /**
   * Establece el {@code correlationId} de la petición en el MDC y en la cabecera de respuesta.
   *
   * <p>Al terminar limpia el MDC completo del hilo en un bloque {@code finally} en lugar de borrar
   * solo su clave: Tomcat reutiliza hilos del pool entre peticiones, así que un borrado parcial
   * podría dejar datos de una petición filtrándose a la siguiente que caiga en el mismo hilo.
   *
   * @param request petición HTTP entrante
   * @param response respuesta HTTP saliente
   * @param filterChain cadena de filtros a ejecutar
   * @throws ServletException si la cadena lanza un error de servlet
   * @throws IOException si la cadena lanza un error de entrada/salida
   */
  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String correlationId =
        Optional.ofNullable(request.getHeader(CORRELATION_ID_HEADER))
            .orElse(UUID.randomUUID().toString());

    MDC.put(MDC_KEY, correlationId);
    response.setHeader(CORRELATION_ID_HEADER, correlationId);

    try {
      filterChain.doFilter(request, response);
    } finally {
      MDC.clear();
    }
  }
}
