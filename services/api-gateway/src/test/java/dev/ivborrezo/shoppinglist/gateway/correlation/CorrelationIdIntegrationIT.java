package dev.ivborrezo.shoppinglist.gateway.correlation;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import dev.ivborrezo.shoppinglist.gateway.logging.AccessLogWebFilter;
import dev.ivborrezo.shoppinglist.gateway.support.GatewayTestDestinations;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.TestConstructor.AutowireMode;
import org.springframework.test.web.reactive.server.ExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Test de integración de la correlación normalizada del borde.
 *
 * <p>Cubre H-04: el valor que recibe el servicio es el mismo que devuelve el eco, siempre con un
 * único valor, aunque el destino escriba el suyo. Aserta también el {@code correlationId} en el
 * {@code MDC} de la línea de acceso.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@TestConstructor(autowireMode = AutowireMode.ALL)
class CorrelationIdIntegrationIT {

  private static final String HEADER = "X-Correlation-Id";

  private static final String DESTINATION_ECHO = "11111111-1111-4111-8111-111111111111";

  private static final GatewayTestDestinations DESTINATIONS = GatewayTestDestinations.start();

  private final WebTestClient webTestClient;

  /**
   * Inyecta el cliente HTTP por constructor.
   *
   * @param webTestClient cliente contra el servidor real en puerto aleatorio
   */
  @Autowired
  CorrelationIdIntegrationIT(WebTestClient webTestClient) {
    this.webTestClient = webTestClient;
  }

  @DynamicPropertySource
  static void destinations(DynamicPropertyRegistry registry) {
    DESTINATIONS.register(registry);
  }

  @AfterAll
  static void stopDestinations() {
    DESTINATIONS.stop();
  }

  @BeforeEach
  void resetStubs() {
    DESTINATIONS.listService().resetAll();
    DESTINATIONS
        .listService()
        .stubFor(
            get(urlPathEqualTo("/lists"))
                .willReturn(okJson("{}").withHeader(HEADER, DESTINATION_ECHO)));
  }

  @Test
  void missingHeader_generatesOneUuid() {
    ExchangeResult result = getLists();

    String correlationId = singleCorrelationId(result);
    assertThat(UUID.fromString(correlationId)).isNotNull();
    assertThat(correlationId).isNotEqualTo(DESTINATION_ECHO);
    verifyForwardedWith(correlationId);
  }

  @Test
  void validUuid_isKept() {
    String correlationId = "3F2504E0-4F89-41D3-9A0C-0305E82C3301";

    ExchangeResult result = getWithHeader(correlationId);

    assertThat(result.getResponseHeaders().get(HEADER)).containsExactly(correlationId);
    verifyForwardedWith(correlationId);
  }

  @Test
  void invalidValue_isRegeneratedAndDoesNotCross() {
    ExchangeResult result = getWithHeader("abcdef");

    String correlationId = singleCorrelationId(result);
    assertThat(correlationId).isNotEqualTo("abcdef");
    assertThat(UUID.fromString(correlationId)).isNotNull();
    verifyForwardedWith(correlationId);
    DESTINATIONS
        .listService()
        .verify(0, getRequestedFor(urlPathEqualTo("/lists")).withHeader(HEADER, equalTo("abcdef")));
  }

  @Test
  void multipleCorrelationIds_areReplaced_byOneValue() {
    ExchangeResult result =
        getWithHeaders(
            "3f2504e0-4f89-41d3-9a0c-0305e82c3301", "1b4e28ba-2fa1-11d2-883f-0016d3cca427");

    String correlationId = singleCorrelationId(result);
    assertThat(correlationId)
        .isNotEqualTo("3f2504e0-4f89-41d3-9a0c-0305e82c3301")
        .isNotEqualTo("1b4e28ba-2fa1-11d2-883f-0016d3cca427");
    verifyForwardedWith(correlationId);
  }

  @Test
  void destinationEcho_isDiscarded_inFavourOfGatewayValue() {
    ExchangeResult result = getWithHeader("3f2504e0-4f89-41d3-9a0c-0305e82c3301");

    assertThat(result.getResponseHeaders().get(HEADER))
        .containsExactly("3f2504e0-4f89-41d3-9a0c-0305e82c3301");
    assertThat(result.getResponseHeaders().get(HEADER)).doesNotContain(DESTINATION_ECHO);
  }

  @Test
  void requestLog_containsCorrelationIdInMdc() throws InterruptedException {
    Logger accessLogger = (Logger) LoggerFactory.getLogger(AccessLogWebFilter.class);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    accessLogger.addAppender(appender);

    try {
      ExchangeResult result = getLists();
      String correlationId = singleCorrelationId(result);
      awaitAccessLog(appender);

      assertThat(appender.list)
          .anySatisfy(
              event ->
                  assertThat(event.getMDCPropertyMap())
                      .containsEntry(CorrelationContext.CONTEXT_KEY, correlationId));
    } finally {
      accessLogger.detachAppender(appender);
      appender.stop();
    }
  }

  private ExchangeResult getLists() {
    return webTestClient.get().uri("/lists").exchange().expectStatus().isOk().returnResult();
  }

  private ExchangeResult getWithHeader(String correlationId) {
    return getWithHeaders(correlationId);
  }

  private ExchangeResult getWithHeaders(String... correlationIds) {
    return webTestClient
        .get()
        .uri("/lists")
        .header(HEADER, correlationIds)
        .exchange()
        .expectStatus()
        .isOk()
        .returnResult();
  }

  private String singleCorrelationId(ExchangeResult result) {
    List<String> values = result.getResponseHeaders().get(HEADER);
    assertThat(values).hasSize(1);
    return values.getFirst();
  }

  private void verifyForwardedWith(String correlationId) {
    DESTINATIONS
        .listService()
        .verify(
            1,
            getRequestedFor(urlPathEqualTo("/lists")).withHeader(HEADER, equalTo(correlationId)));
  }

  private void awaitAccessLog(ListAppender<ILoggingEvent> appender) throws InterruptedException {
    long deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
    while (appender.list.isEmpty() && System.nanoTime() < deadlineNanos) {
      Thread.sleep(25);
    }
  }
}
