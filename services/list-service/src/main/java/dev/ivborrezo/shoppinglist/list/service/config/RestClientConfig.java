package dev.ivborrezo.shoppinglist.list.service.config;

import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Duration;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Configuración del cliente HTTP hacia {@code product-service}.
 *
 * <p>Registra un {@link RestClient} con la URL base y los timeouts del servicio y con un
 * interceptor que propaga la correlación y el idioma de la petición entrante.
 */
@Configuration
public class RestClientConfig {

  /**
   * Construye el {@link RestClient} hacia {@code product-service}.
   *
   * <p>Aplica los timeouts de conexión y lectura configurados y propaga las cabeceras de
   * correlación e idioma de la petición entrante.
   *
   * @param builder builder autoconfigurado de Spring Boot del que parte el cliente
   * @param baseUrl URL base del servicio de productos
   * @param connectTimeout timeout de establecimiento de conexión
   * @param readTimeout timeout de lectura de la respuesta
   * @return cliente HTTP configurado para el servicio de productos
   */
  @Bean
  public RestClient productServiceRestClient(
      RestClient.Builder builder,
      @Value("${product-service.client.base-url}") String baseUrl,
      @Value("${product-service.client.connect-timeout}") Duration connectTimeout,
      @Value("${product-service.client.read-timeout}") Duration readTimeout) {
    HttpClient httpClient = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
    JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
    requestFactory.setReadTimeout(readTimeout);
    return builder
        .baseUrl(baseUrl)
        .requestFactory(requestFactory)
        .requestInterceptor(new HeaderPropagationInterceptor())
        .build();
  }

  /**
   * Interceptor que reenvía a {@code product-service} la correlación y el idioma de la petición
   * entrante.
   *
   * <p>Solo propaga las cabeceras con valor presente: una lista blanca explícita en lugar de copiar
   * todas las cabeceras entrantes.
   */
  private static final class HeaderPropagationInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(
        HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
      String correlationId = MDC.get("correlationId");
      if (correlationId != null && !correlationId.isBlank()) {
        request.getHeaders().set("X-Correlation-Id", correlationId);
      }

      if (RequestContextHolder.getRequestAttributes()
          instanceof ServletRequestAttributes attributes) {
        String acceptLanguage = attributes.getRequest().getHeader("Accept-Language");
        if (acceptLanguage != null && !acceptLanguage.isBlank()) {
          request.getHeaders().set("Accept-Language", acceptLanguage);
        }
      }

      return execution.execute(request, body);
    }
  }
}
