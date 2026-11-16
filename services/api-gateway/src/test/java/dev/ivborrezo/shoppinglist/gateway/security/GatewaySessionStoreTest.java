package dev.ivborrezo.shoppinglist.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ivborrezo.shoppinglist.gateway.support.MutableClock;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebSession;
import org.springframework.web.server.session.DefaultWebSessionManager;
import org.springframework.web.server.session.InMemoryWebSessionStore;

class GatewaySessionStoreTest {

  private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");

  @Test
  void sessionExpiresAfterConfiguredInactivity() {
    MutableClock clock = new MutableClock(START);
    DefaultWebSessionManager manager =
        GatewaySecurityConfig.createWebSessionManager(
            properties(Duration.ofMinutes(30), 100), clock);
    InMemoryWebSessionStore store = (InMemoryWebSessionStore) manager.getSessionStore();
    WebSession session = saveSession(store);

    clock.advance(Duration.ofMinutes(31));

    assertThat(store.retrieveSession(session.getId()).block()).isNull();
  }

  @Test
  void sessionAccessedWithinInactivityTtl_remainsAvailable() {
    MutableClock clock = new MutableClock(START);
    DefaultWebSessionManager manager =
        GatewaySecurityConfig.createWebSessionManager(
            properties(Duration.ofMinutes(30), 100), clock);
    InMemoryWebSessionStore store = (InMemoryWebSessionStore) manager.getSessionStore();
    WebSession session = saveSession(store);

    clock.advance(Duration.ofMinutes(29));

    assertThat(store.retrieveSession(session.getId()).block()).isNotNull();
  }

  @Test
  void creatingMoreThanMaxSessions_failsWithIllegalState() {
    DefaultWebSessionManager manager =
        GatewaySecurityConfig.createWebSessionManager(
            properties(Duration.ofMinutes(30), 2), new MutableClock(START));
    InMemoryWebSessionStore store = (InMemoryWebSessionStore) manager.getSessionStore();

    saveSession(store);
    saveSession(store);

    WebSession extraSession = store.createWebSession().block();
    extraSession.start();

    assertThat(extraSession).isNotNull();
    assertThatThrownBy(() -> extraSession.save().block())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageStartingWith("Max sessions limit reached");
  }

  @Test
  void configuredLimits_areAppliedToTheStore() {
    DefaultWebSessionManager manager =
        GatewaySecurityConfig.createWebSessionManager(
            properties(Duration.ofMinutes(15), 7), new MutableClock(START));
    InMemoryWebSessionStore store = (InMemoryWebSessionStore) manager.getSessionStore();

    assertThat(store.getDefaultMaxIdleTime()).isEqualTo(Duration.ofMinutes(15));
    assertThat(store.getMaxSessions()).isEqualTo(7);
  }

  @Test
  void sessionCookie_isHttpOnlyAndSameSiteLax() {
    DefaultWebSessionManager manager =
        GatewaySecurityConfig.createWebSessionManager(
            properties(Duration.ofMinutes(30), 100), new MutableClock(START));
    MockServerWebExchange exchange =
        MockServerWebExchange.from(MockServerHttpRequest.get("/lists").build());

    manager.getSessionIdResolver().setSessionId(exchange, "session-id");

    ResponseCookie cookie = exchange.getResponse().getCookies().getFirst("SESSION");
    assertThat(cookie).isNotNull();
    assertThat(cookie.isHttpOnly()).isTrue();
    assertThat(cookie.getSameSite()).isEqualTo("Lax");
  }

  private static WebSession saveSession(InMemoryWebSessionStore store) {
    WebSession session = store.createWebSession().block();
    session.start();
    session.save().block();
    return session;
  }

  private static GatewaySecurityProperties properties(Duration sessionTtl, int maxSessions) {
    return new GatewaySecurityProperties(
        "http://test-oidc.local/realms/shopping-list-test",
        "sub",
        "roles",
        sessionTtl,
        maxSessions);
  }
}
