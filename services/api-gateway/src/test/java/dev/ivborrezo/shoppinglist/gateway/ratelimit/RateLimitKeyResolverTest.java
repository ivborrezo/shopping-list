package dev.ivborrezo.shoppinglist.gateway.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

class RateLimitKeyResolverTest {

  private static final String FORWARDED_FOR = "X-Forwarded-For";

  private final RateLimitKeyResolver resolver = new RemoteAddressKeyResolver();

  @Test
  void resolve_usesRemoteSocketAddress() {
    MockServerWebExchange exchange = exchangeFrom("203.0.113.7");

    assertThat(resolver.resolve(exchange)).isEqualTo("203.0.113.7");
  }

  @Test
  void resolve_withoutRemoteAddress_sharesTheSameKey() {
    MockServerWebExchange first = MockServerWebExchange.from(MockServerHttpRequest.get("/lists"));
    MockServerWebExchange second = MockServerWebExchange.from(MockServerHttpRequest.get("/lists"));

    assertThat(resolver.resolve(first)).isEqualTo(resolver.resolve(second));
  }

  @Test
  void resolve_ignoresForwardedForHeader() {
    MockServerWebExchange exchange =
        MockServerWebExchange.from(
            MockServerHttpRequest.get("/lists")
                .header(FORWARDED_FOR, "198.51.100.9")
                .remoteAddress(new InetSocketAddress("203.0.113.7", 41000)));

    assertThat(resolver.resolve(exchange)).isEqualTo("203.0.113.7");
  }

  private MockServerWebExchange exchangeFrom(String address) {
    return MockServerWebExchange.from(
        MockServerHttpRequest.get("/lists")
            .header(HttpHeaders.HOST, "gateway.test")
            .remoteAddress(new InetSocketAddress(address, 41000)));
  }
}
