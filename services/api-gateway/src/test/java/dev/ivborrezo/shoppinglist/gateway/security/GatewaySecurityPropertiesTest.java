package dev.ivborrezo.shoppinglist.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class GatewaySecurityPropertiesTest {

  @Test
  void defaultValues_areAppliedWhenOnlyIssuerIsConfigured() {
    GatewaySecurityProperties properties = bind(Map.of("issuer-uri", "http://issuer.test"));

    assertThat(properties.issuerUri()).isEqualTo("http://issuer.test");
    assertThat(properties.principalClaim()).isEqualTo("sub");
    assertThat(properties.rolesClaim()).isEqualTo("roles");
    assertThat(properties.sessionTtl()).isEqualTo(Duration.ofMinutes(30));
    assertThat(properties.maxSessions()).isEqualTo(10_000);
  }

  @Test
  void configuredValues_overrideDefaults() {
    Map<String, String> source = new LinkedHashMap<>();
    source.put("issuer-uri", "http://issuer.test");
    source.put("principal-claim", "user_id");
    source.put("roles-claim", "realm_roles");
    source.put("session-ttl", "5m");
    source.put("max-sessions", "42");

    GatewaySecurityProperties properties = bind(source);

    assertThat(properties.principalClaim()).isEqualTo("user_id");
    assertThat(properties.rolesClaim()).isEqualTo("realm_roles");
    assertThat(properties.sessionTtl()).isEqualTo(Duration.ofMinutes(5));
    assertThat(properties.maxSessions()).isEqualTo(42);
  }

  @Test
  void missingIssuer_failsBinding() {
    assertThatThrownBy(() -> bind(Map.of("principal-claim", "sub")))
        .hasRootCauseMessage("api-gateway.security.issuer-uri must be configured");
  }

  @Test
  void nonPositiveSessionTtl_isRejected() {
    assertThatThrownBy(() -> bind(Map.of("issuer-uri", "http://issuer.test", "session-ttl", "0s")))
        .hasRootCauseMessage("api-gateway.security.session-ttl must be positive");
  }

  @Test
  void nonPositiveMaxSessions_isRejected() {
    assertThatThrownBy(() -> bind(Map.of("issuer-uri", "http://issuer.test", "max-sessions", "0")))
        .hasRootCauseMessage("api-gateway.security.max-sessions must be positive");
  }

  private GatewaySecurityProperties bind(Map<String, String> values) {
    MapConfigurationPropertySource source =
        new MapConfigurationPropertySource(prefix(values, "api-gateway.security."));
    return new Binder(source)
        .bind("api-gateway.security", Bindable.of(GatewaySecurityProperties.class))
        .orElseThrow(
            () -> new IllegalStateException("api-gateway.security did not bind to properties"));
  }

  private Map<String, Object> prefix(Map<String, String> values, String prefix) {
    Map<String, Object> prefixed = new LinkedHashMap<>();
    values.forEach((key, value) -> prefixed.put(prefix + key, value));
    return prefixed;
  }
}
