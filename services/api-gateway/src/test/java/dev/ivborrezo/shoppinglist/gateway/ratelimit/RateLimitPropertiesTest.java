package dev.ivborrezo.shoppinglist.gateway.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class RateLimitPropertiesTest {

  @Test
  void constructor_withPositiveCapacityAndWindow_exposesValues() {
    RateLimitProperties properties = new RateLimitProperties(600, Duration.ofSeconds(60));

    assertThat(properties.requests()).isEqualTo(600);
    assertThat(properties.window()).isEqualTo(Duration.ofSeconds(60));
  }

  @Test
  void constructor_withZeroRequests_rejectsWithPropertyName() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new RateLimitProperties(0, Duration.ofSeconds(60)))
        .withMessageContaining("api-gateway.rate-limit.requests");
  }

  @Test
  void constructor_withNegativeRequests_rejectsWithPropertyName() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new RateLimitProperties(-1, Duration.ofSeconds(60)))
        .withMessageContaining("api-gateway.rate-limit.requests");
  }

  @Test
  void constructor_withNullWindow_rejectsWithPropertyName() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new RateLimitProperties(600, null))
        .withMessageContaining("api-gateway.rate-limit.window");
  }

  @Test
  void constructor_withZeroWindow_rejectsWithPropertyName() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new RateLimitProperties(600, Duration.ZERO))
        .withMessageContaining("api-gateway.rate-limit.window");
  }

  @Test
  void constructor_withNegativeWindow_rejectsWithPropertyName() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new RateLimitProperties(600, Duration.ofSeconds(-5)))
        .withMessageContaining("api-gateway.rate-limit.window");
  }
}
