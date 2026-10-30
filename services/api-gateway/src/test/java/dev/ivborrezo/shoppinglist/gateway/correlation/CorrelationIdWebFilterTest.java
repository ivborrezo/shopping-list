package dev.ivborrezo.shoppinglist.gateway.correlation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

class CorrelationIdWebFilterTest {

  private static final String HEADER = "X-Correlation-Id";

  private final CorrelationIdWebFilter filter = new CorrelationIdWebFilter();

  @Test
  void missingHeader_generatesUuid() {
    ServerWebExchange forwarded = forward(MockServerHttpRequest.get("/lists").build());

    String correlationId = forwarded.getRequest().getHeaders().getFirst(HEADER);
    assertThat(correlationId).isNotNull();
    assertThat(correlationId).hasSize(36);
    assertThat(UUID.fromString(correlationId)).isNotNull();
  }

  @Test
  void validUuid_isKept() {
    String correlationId = "3F2504E0-4F89-41D3-9A0C-0305E82C3301";

    ServerWebExchange forwarded =
        forward(MockServerHttpRequest.get("/lists").header(HEADER, correlationId).build());

    assertThat(forwarded.getRequest().getHeaders().get(HEADER)).containsExactly(correlationId);
  }

  @Test
  void invalidValue_isReplaced() {
    ServerWebExchange forwarded =
        forward(MockServerHttpRequest.get("/lists").header(HEADER, "abcdef").build());

    String correlationId = forwarded.getRequest().getHeaders().getFirst(HEADER);
    assertThat(correlationId).isNotEqualTo("abcdef");
    assertThat(UUID.fromString(correlationId)).isNotNull();
  }

  @Test
  void uuidWithoutHyphens_isReplaced() {
    String compactUuid = "3f2504e04f8941d39a0c0305e82c3301";

    ServerWebExchange forwarded =
        forward(MockServerHttpRequest.get("/lists").header(HEADER, compactUuid).build());

    String correlationId = forwarded.getRequest().getHeaders().getFirst(HEADER);
    assertThat(correlationId).isNotEqualTo(compactUuid);
    assertThat(UUID.fromString(correlationId)).isNotNull();
  }

  @Test
  void multipleValues_areReplaced() {
    ServerWebExchange forwarded =
        forward(
            MockServerHttpRequest.get("/lists")
                .header(
                    HEADER,
                    "3f2504e0-4f89-41d3-9a0c-0305e82c3301",
                    "1b4e28ba-2fa1-11d2-883f-0016d3cca427")
                .build());

    List<String> values = forwarded.getRequest().getHeaders().get(HEADER);
    assertThat(values).hasSize(1);
    assertThat(values.getFirst())
        .isNotEqualTo("3f2504e0-4f89-41d3-9a0c-0305e82c3301")
        .isNotEqualTo("1b4e28ba-2fa1-11d2-883f-0016d3cca427");
    assertThat(UUID.fromString(values.getFirst())).isNotNull();
  }

  @Test
  void headerWithCrlf_isReplaced() {
    ServerWebExchange forwarded =
        forward(
            MockServerHttpRequest.get("/lists")
                .header(HEADER, "3f2504e0-4f89-41d3-9a0c-0305e82c3301\r\nX-Injected: value")
                .build());

    assertThat(forwarded.getRequest().getHeaders().get(HEADER)).hasSize(1);
    assertThat(forwarded.getRequest().getHeaders().getFirst(HEADER)).doesNotContain("X-Injected");
  }

  @Test
  void outgoingRequest_usesSetNotAdd() {
    ServerWebExchange forwarded =
        forward(
            MockServerHttpRequest.get("/lists")
                .header(HEADER, "3f2504e0-4f89-41d3-9a0c-0305e82c3301")
                .header(HEADER, "1b4e28ba-2fa1-11d2-883f-0016d3cca427")
                .build());

    assertThat(forwarded.getRequest().getHeaders().get(HEADER)).hasSize(1);
  }

  private ServerWebExchange forward(MockServerHttpRequest request) {
    MockServerWebExchange exchange = MockServerWebExchange.from(request);
    AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
    WebFilterChain chain =
        filteredExchange -> {
          forwarded.set(filteredExchange);
          return Mono.empty();
        };

    filter.filter(exchange, chain).block();

    return forwarded.get();
  }
}
