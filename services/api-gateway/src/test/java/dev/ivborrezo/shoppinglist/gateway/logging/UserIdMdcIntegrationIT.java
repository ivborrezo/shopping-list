package dev.ivborrezo.shoppinglist.gateway.logging;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import dev.ivborrezo.shoppinglist.gateway.correlation.CorrelationContext;
import dev.ivborrezo.shoppinglist.gateway.support.GatewayTestDestinations;
import dev.ivborrezo.shoppinglist.gateway.support.OidcLoginTestClient;
import dev.ivborrezo.shoppinglist.gateway.support.TestOidcProvider;
import dev.ivborrezo.shoppinglist.testsupport.TestJwtFactory;
import dev.ivborrezo.shoppinglist.testsupport.TestUsers;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.TestConstructor.AutowireMode;
import org.springframework.test.web.reactive.server.ExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Test de integración de la trazabilidad por usuario en los logs del borde.
 *
 * <p>Verifica que la línea de acceso incluye {@code userId} y {@code correlationId} tanto en el
 * canal {@code Bearer} como en el de sesión OIDC, que una petición posterior sin credenciales no
 * arrastra el usuario y que ningún log registra tokens, cabeceras de autorización, cookies de
 * sesión ni el secreto del cliente.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@TestConstructor(autowireMode = AutowireMode.ALL)
class UserIdMdcIntegrationIT {

  private static final GatewayTestDestinations DESTINATIONS = GatewayTestDestinations.start();

  private final WebTestClient webTestClient;

  private final OidcLoginTestClient loginClient;

  private final ListAppender<ILoggingEvent> accessLogAppender = new ListAppender<>();

  private final ListAppender<ILoggingEvent> rootAppender = new ListAppender<>();

  private final Logger accessLogger = (Logger) LoggerFactory.getLogger(AccessLogWebFilter.class);

  private final Logger rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);

  /**
   * Inyecta el cliente HTTP y prepara el flujo de login.
   *
   * @param webTestClient cliente contra el servidor real en puerto aleatorio
   */
  @Autowired
  UserIdMdcIntegrationIT(WebTestClient webTestClient) {
    this.webTestClient = webTestClient;
    this.loginClient = new OidcLoginTestClient(webTestClient, DESTINATIONS.oidcProvider());
  }

  @DynamicPropertySource
  static void destinations(DynamicPropertyRegistry registry) {
    DESTINATIONS.register(registry);
  }

  @BeforeEach
  void startAppenders() {
    accessLogAppender.start();
    accessLogger.addAppender(accessLogAppender);
    rootAppender.start();
    rootLogger.addAppender(rootAppender);
    DESTINATIONS.productService().resetAll();
    DESTINATIONS.listService().resetAll();
    DESTINATIONS.listService().stubFor(get(urlPathEqualTo("/lists")).willReturn(okJson("{}")));
  }

  @AfterEach
  void stopAppenders() {
    accessLogger.detachAppender(accessLogAppender);
    accessLogAppender.stop();
    rootLogger.detachAppender(rootAppender);
    rootAppender.stop();
  }

  @AfterAll
  static void stopDestinations() {
    DESTINATIONS.stop();
  }

  @Test
  void bearerRequest_accessLogContainsUserIdAndCorrelationId() throws InterruptedException {
    String token = TestJwtFactory.token().subject(TestUsers.USER_1).build();

    ExchangeResult result =
        webTestClient
            .get()
            .uri("/lists")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            .exchange()
            .expectStatus()
            .isOk()
            .returnResult();

    ILoggingEvent accessLog = awaitAccessLog("/lists");
    assertThat(accessLog.getMDCPropertyMap())
        .containsEntry(CorrelationContext.USER_ID_CONTEXT_KEY, TestUsers.USER_1.toString())
        .containsEntry(
            CorrelationContext.CONTEXT_KEY,
            result.getResponseHeaders().getFirst(CorrelationContext.HEADER_NAME));
  }

  @Test
  void sessionRequest_accessLogContainsUserIdAndCorrelationId() throws InterruptedException {
    OidcLoginTestClient.LoginSession session = loginClient.login();

    ExchangeResult result =
        webTestClient
            .get()
            .uri("/lists")
            .cookie(OidcLoginTestClient.SESSION_COOKIE, session.sessionCookie())
            .exchange()
            .expectStatus()
            .isOk()
            .returnResult();

    ILoggingEvent accessLog = awaitAccessLog("/lists");
    assertThat(accessLog.getMDCPropertyMap())
        .containsEntry(CorrelationContext.USER_ID_CONTEXT_KEY, TestUsers.USER_1.toString())
        .containsEntry(
            CorrelationContext.CONTEXT_KEY,
            result.getResponseHeaders().getFirst(CorrelationContext.HEADER_NAME));
  }

  @Test
  void anonymousRequestAfterAuthenticatedOne_doesNotInheritUserId() throws InterruptedException {
    String token = TestJwtFactory.token().subject(TestUsers.USER_1).build();
    webTestClient
        .get()
        .uri("/lists")
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
        .exchange()
        .expectStatus()
        .isOk();
    awaitAccessLog("/lists");
    accessLogAppender.list.clear();

    webTestClient.get().uri("/actuator/health").exchange().expectStatus().isOk();

    ILoggingEvent accessLog = awaitAccessLog("/actuator/health");
    assertThat(accessLog.getMDCPropertyMap())
        .doesNotContainKey(CorrelationContext.USER_ID_CONTEXT_KEY);
  }

  @Test
  void logs_neverExposeTokensCookiesOrClientSecret() throws InterruptedException {
    String token = TestJwtFactory.token().subject(TestUsers.USER_2).build();
    webTestClient
        .get()
        .uri("/lists")
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
        .exchange()
        .expectStatus()
        .isOk();

    OidcLoginTestClient.LoginSession session = loginClient.login();
    webTestClient
        .get()
        .uri("/lists")
        .cookie(OidcLoginTestClient.SESSION_COOKIE, session.sessionCookie())
        .exchange()
        .expectStatus()
        .isOk();

    awaitAccessLog("/lists");
    rootAppender.stop();
    List<String> loggedValues = rootAppender.list.stream().map(this::rendered).toList();

    assertThat(loggedValues)
        .noneSatisfy(value -> assertThat(value).contains(token))
        .noneSatisfy(value -> assertThat(value).contains("Bearer "))
        .noneSatisfy(value -> assertThat(value).contains(session.sessionCookie()))
        .noneSatisfy(value -> assertThat(value).contains(TestOidcProvider.ACCESS_TOKEN))
        .noneSatisfy(value -> assertThat(value).contains(TestOidcProvider.CLIENT_SECRET));
  }

  private String rendered(ILoggingEvent event) {
    String message = event.getFormattedMessage();
    Map<String, String> mdc = event.getMDCPropertyMap();
    return message + " " + mdc;
  }

  private ILoggingEvent awaitAccessLog(String path) throws InterruptedException {
    long deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
    while (System.nanoTime() < deadlineNanos) {
      for (ILoggingEvent event : accessLogAppender.list) {
        if (event.getFormattedMessage().contains(path)) {
          return event;
        }
      }
      Thread.sleep(25);
    }
    throw new AssertionError("No access log line found for " + path);
  }
}
