package dev.ivborrezo.shoppinglist.gateway.error;

import static org.assertj.core.api.Assertions.assertThat;

import io.netty.channel.ConnectTimeoutException;
import io.netty.handler.timeout.ReadTimeoutException;
import java.net.ConnectException;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.json.ProblemDetailJacksonMixin;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.resource.NoResourceFoundException;
import org.springframework.web.server.ResponseStatusException;
import reactor.netty.http.client.PrematureCloseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class ProblemDetailErrorWriterTest {

  private final ObjectMapper objectMapper =
      JsonMapper.builder().addMixIn(ProblemDetail.class, ProblemDetailJacksonMixin.class).build();

  private final BorderErrorMapper errorMapper = new BorderErrorMapper();

  private final ProblemDetailErrorWriter errorWriter =
      new ProblemDetailErrorWriter(errorMapper, objectMapper);

  @Test
  void responseStatusNotFound_isMappedToRouteNotFound() {
    ErrorCode code = errorMapper.map(new ResponseStatusException(HttpStatus.NOT_FOUND));

    assertThat(code).isEqualTo(ErrorCode.ROUTE_NOT_FOUND);
  }

  @Test
  void noResourceFound_isMappedToRouteNotFound() {
    ErrorCode code =
        errorMapper.map(new NoResourceFoundException(URI.create("/dataviz"), "dataviz"));

    assertThat(code).isEqualTo(ErrorCode.ROUTE_NOT_FOUND);
  }

  @Test
  void readTimeout_isMappedToServiceUnavailable() {
    ErrorCode code = errorMapper.map(ReadTimeoutException.INSTANCE);

    assertThat(code).isEqualTo(ErrorCode.SERVICE_UNAVAILABLE);
  }

  @Test
  void connectTimeout_isMappedToServiceUnavailable() {
    ErrorCode code = errorMapper.map(new ConnectTimeoutException());

    assertThat(code).isEqualTo(ErrorCode.SERVICE_UNAVAILABLE);
  }

  @Test
  void connectionRefused_isMappedToServiceUnavailable() {
    ErrorCode code = errorMapper.map(new ConnectException("Connection refused"));

    assertThat(code).isEqualTo(ErrorCode.SERVICE_UNAVAILABLE);
  }

  @Test
  void prematureClose_isMappedToServiceUnavailable() {
    ErrorCode code = errorMapper.map(PrematureCloseException.TEST_EXCEPTION);

    assertThat(code).isEqualTo(ErrorCode.SERVICE_UNAVAILABLE);
  }

  @Test
  void nestedTransportFailure_isMappedToServiceUnavailable() {
    ErrorCode code =
        errorMapper.map(new RuntimeException(new ConnectException("Connection refused")));

    assertThat(code).isEqualTo(ErrorCode.SERVICE_UNAVAILABLE);
  }

  @Test
  void gatewayTimeout_isMappedToServiceUnavailable() {
    ErrorCode code = errorMapper.map(new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT));

    assertThat(code).isEqualTo(ErrorCode.SERVICE_UNAVAILABLE);
  }

  @Test
  void badGateway_isMappedToServiceUnavailable() {
    ErrorCode code = errorMapper.map(new ResponseStatusException(HttpStatus.BAD_GATEWAY));

    assertThat(code).isEqualTo(ErrorCode.SERVICE_UNAVAILABLE);
  }

  @Test
  void unhandledException_isMappedToInternalError() {
    ErrorCode code = errorMapper.map(new IllegalStateException("boom"));

    assertThat(code).isEqualTo(ErrorCode.INTERNAL_ERROR);
  }

  @Test
  void routeNotFound_writesProblemDetailWithCorrelationOfPath() {
    MockServerWebExchange exchange =
        MockServerWebExchange.from(MockServerHttpRequest.get("/dataviz").build());

    errorWriter.handle(exchange, new ResponseStatusException(HttpStatus.NOT_FOUND)).block();

    JsonNode body = bodyOf(exchange);
    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(exchange.getResponse().getHeaders().getContentType())
        .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    assertThat(body.get("type").asString()).isEqualTo("about:blank");
    assertThat(body.get("title").asString()).isEqualTo("Route not found");
    assertThat(body.get("status").intValue()).isEqualTo(404);
    assertThat(body.get("instance").asString()).isEqualTo("/dataviz");
    assertThat(body.get("code").asString()).isEqualTo("ROUTE_NOT_FOUND");
  }

  @Test
  void serviceUnavailable_doesNotLeakTopology() {
    MockServerWebExchange exchange =
        MockServerWebExchange.from(MockServerHttpRequest.get("/lists").build());

    errorWriter
        .handle(exchange, new ConnectException("Connection refused: localhost:8082"))
        .block();

    String body = exchange.getResponse().getBodyAsString().block();
    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    assertThat(body).doesNotContain("localhost").doesNotContain("8082");
    assertThat(bodyOf(exchange).get("code").asString()).isEqualTo("SERVICE_UNAVAILABLE");
  }

  @Test
  void internalError_doesNotLeakOriginalMessage() {
    MockServerWebExchange exchange =
        MockServerWebExchange.from(MockServerHttpRequest.get("/lists").build());

    errorWriter.handle(exchange, new IllegalStateException("secret internal detail")).block();

    String body = exchange.getResponse().getBodyAsString().block();
    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(body).doesNotContain("secret internal detail");
    assertThat(bodyOf(exchange).get("code").asString()).isEqualTo("INTERNAL_ERROR");
    assertThat(bodyOf(exchange).get("title").asString()).isEqualTo("Internal Server Error");
  }

  private JsonNode bodyOf(MockServerWebExchange exchange) {
    return objectMapper.readTree(exchange.getResponse().getBodyAsString().block());
  }
}
