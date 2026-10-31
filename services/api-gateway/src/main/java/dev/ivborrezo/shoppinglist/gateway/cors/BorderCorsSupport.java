package dev.ivborrezo.shoppinglist.gateway.cors;

import org.springframework.cloud.gateway.config.GlobalCorsProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import org.springframework.web.server.ServerWebExchange;

/**
 * Aplica la política CORS a las respuestas que no pasan por el procesador del framework.
 *
 * <p>Lee la misma configuración global que usa el gateway, de modo que la whitelist, el reflejo del
 * origen y las cabeceras expuestas tienen una única fuente. La whitelist es exacta: los patrones de
 * origen no cuentan como orígenes permitidos. Cuando el origen de la petición está permitido,
 * refleja el origen, expone las cabeceras configuradas y añade {@code Vary: Origin}; cuando no lo
 * está, no añade nada. No gestiona el preflight: esa respuesta la produce el procesador CORS.
 */
@Component
public class BorderCorsSupport {

  private final UrlBasedCorsConfigurationSource configurationSource;

  /**
   * Enlaza la política global y valida que declare al menos un origen permitido.
   *
   * @param globalCorsProperties configuración global compartida con el procesador CORS
   * @throws IllegalStateException si la configuración no declara ningún origen permitido
   */
  public BorderCorsSupport(GlobalCorsProperties globalCorsProperties) {
    if (!hasAllowedOrigins(globalCorsProperties)) {
      throw new IllegalStateException(
          "No CORS origins configured under"
              + " spring.cloud.gateway.server.webflux.globalcors.cors-configurations");
    }
    configurationSource = new UrlBasedCorsConfigurationSource();
    configurationSource.setCorsConfigurations(globalCorsProperties.getCorsConfigurations());
  }

  /**
   * Refleja el origen y expone las cabeceras configuradas si el origen está permitido.
   *
   * @param exchange intercambio cuya respuesta se está completando
   */
  public void apply(ServerWebExchange exchange) {
    CorsConfiguration configuration = configurationSource.getCorsConfiguration(exchange);
    if (configuration == null) {
      return;
    }
    String allowedOrigin =
        configuration.checkOrigin(exchange.getRequest().getHeaders().getOrigin());
    if (allowedOrigin == null) {
      return;
    }

    HttpHeaders headers = exchange.getResponse().getHeaders();
    headers.setAccessControlAllowOrigin(allowedOrigin);
    if (!CollectionUtils.isEmpty(configuration.getExposedHeaders())) {
      headers.setAccessControlExposeHeaders(configuration.getExposedHeaders());
    }
    if (!headers.getVary().contains(HttpHeaders.ORIGIN)) {
      headers.add(HttpHeaders.VARY, HttpHeaders.ORIGIN);
    }
  }

  private static boolean hasAllowedOrigins(GlobalCorsProperties globalCorsProperties) {
    return globalCorsProperties.getCorsConfigurations().values().stream()
        .anyMatch(configuration -> !CollectionUtils.isEmpty(configuration.getAllowedOrigins()));
  }
}
