package dev.ivborrezo.shoppinglist.gateway.cors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ivborrezo.shoppinglist.gateway.correlation.CorrelationContext;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.config.GlobalCorsProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.cors.CorsConfiguration;

class BorderCorsSupportTest {

  private static final String ALLOWED_ORIGIN = "http://app.test";

  @Test
  void allowedOrigin_reflectsOriginAndExposesCorrelationId() {
    BorderCorsSupport support = new BorderCorsSupport(corsProperties(ALLOWED_ORIGIN));
    MockServerWebExchange exchange = exchangeWithOrigin(ALLOWED_ORIGIN);

    support.apply(exchange);

    HttpHeaders headers = exchange.getResponse().getHeaders();
    assertThat(headers.getAccessControlAllowOrigin()).isEqualTo(ALLOWED_ORIGIN);
    assertThat(headers.getAccessControlExposeHeaders())
        .containsExactly(CorrelationContext.HEADER_NAME);
    assertThat(headers.getVary()).contains(HttpHeaders.ORIGIN);
    assertThat(headers.getAccessControlAllowCredentials()).isFalse();
  }

  @Test
  void notAllowedOrigin_addsNoCorsHeaders() {
    BorderCorsSupport support = new BorderCorsSupport(corsProperties(ALLOWED_ORIGIN));
    MockServerWebExchange exchange = exchangeWithOrigin("http://unknown.test");

    support.apply(exchange);

    HttpHeaders headers = exchange.getResponse().getHeaders();
    assertThat(headers.getAccessControlAllowOrigin()).isNull();
    assertThat(headers.getAccessControlExposeHeaders()).isEmpty();
    assertThat(headers.getVary()).doesNotContain(HttpHeaders.ORIGIN);
  }

  @Test
  void requestWithoutOrigin_addsNoCorsHeaders() {
    BorderCorsSupport support = new BorderCorsSupport(corsProperties(ALLOWED_ORIGIN));
    MockServerWebExchange exchange =
        MockServerWebExchange.from(MockServerHttpRequest.get("/lists").build());

    support.apply(exchange);

    HttpHeaders headers = exchange.getResponse().getHeaders();
    assertThat(headers.getAccessControlAllowOrigin()).isNull();
    assertThat(headers.getVary()).doesNotContain(HttpHeaders.ORIGIN);
  }

  @Test
  void emptyOrigins_failFast() {
    GlobalCorsProperties properties = new GlobalCorsProperties();

    assertThatThrownBy(() -> new BorderCorsSupport(properties))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("spring.cloud.gateway.server.webflux.globalcors");
  }

  private static MockServerWebExchange exchangeWithOrigin(String origin) {
    return MockServerWebExchange.from(
        MockServerHttpRequest.get("/lists").header(HttpHeaders.ORIGIN, origin).build());
  }

  private static GlobalCorsProperties corsProperties(String... origins) {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(List.of(origins));
    configuration.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE"));
    configuration.setExposedHeaders(List.of(CorrelationContext.HEADER_NAME));
    GlobalCorsProperties properties = new GlobalCorsProperties();
    properties.getCorsConfigurations().put("/**", configuration);
    return properties;
  }
}
