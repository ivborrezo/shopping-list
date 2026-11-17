package dev.ivborrezo.shoppinglist.gateway.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ivborrezo.shoppinglist.gateway.security.GatewaySecurityProperties;
import dev.ivborrezo.shoppinglist.testsupport.TestUsers;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class RateLimitKeyResolverTest {

  private static final String FORWARDED_FOR = "X-Forwarded-For";

  private static final String ADDRESS = "203.0.113.7";

  private final RemoteAddressKeyResolver resolver = new RemoteAddressKeyResolver(properties());

  @Test
  void unauthenticatedRequest_resolvesIpKey() {
    assertThat(resolver.resolve(exchangeFrom(ADDRESS)).block()).isEqualTo("ip:" + ADDRESS);
  }

  @Test
  void requestWithoutRemoteAddress_resolvesUnknownClientKey() {
    String key =
        resolver.resolve(MockServerWebExchange.from(MockServerHttpRequest.get("/lists"))).block();

    assertThat(key).isEqualTo("ip:unknown-client");
  }

  @Test
  void forwardedForHeader_isIgnored() {
    MockServerWebExchange exchange =
        MockServerWebExchange.from(
            MockServerHttpRequest.get("/lists")
                .header(FORWARDED_FOR, "198.51.100.9")
                .remoteAddress(new InetSocketAddress(ADDRESS, 41000)));

    assertThat(resolver.resolve(exchange).block()).isEqualTo("ip:" + ADDRESS);
  }

  @Test
  void bearerAuthentication_resolvesUserKey() {
    Jwt jwt =
        Jwt.withTokenValue("token")
            .header("alg", "RS256")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(60))
            .claim("sub", TestUsers.USER_1.toString())
            .build();
    JwtAuthenticationToken authentication =
        new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_USER")));

    assertThat(resolveAs(exchangeFrom(ADDRESS), authentication))
        .isEqualTo("user:" + TestUsers.USER_1);
  }

  @Test
  void sessionAuthentication_resolvesUserKey() {
    DefaultOAuth2User principal =
        new DefaultOAuth2User(
            List.of(new SimpleGrantedAuthority("ROLE_USER")),
            Map.of("sub", TestUsers.USER_2.toString()),
            "sub");
    OAuth2AuthenticationToken authentication =
        new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "keycloak");

    assertThat(resolveAs(exchangeFrom(ADDRESS), authentication))
        .isEqualTo("user:" + TestUsers.USER_2);
  }

  @Test
  void nonUuidSubject_fallsBackToIpKey() {
    TestingAuthenticationToken authentication =
        new TestingAuthenticationToken("not-a-uuid", "credentials");

    assertThat(resolveAs(exchangeFrom(ADDRESS), authentication)).isEqualTo("ip:" + ADDRESS);
  }

  @Test
  void resolveIpKey_usesSocketAddress() {
    assertThat(resolver.resolveIpKey(exchangeFrom(ADDRESS))).isEqualTo("ip:" + ADDRESS);
  }

  private String resolveAs(
      MockServerWebExchange exchange, @Nullable Authentication authentication) {
    if (authentication == null) {
      return resolver.resolve(exchange).block();
    }
    return resolver
        .resolve(exchange)
        .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication))
        .block();
  }

  private static MockServerWebExchange exchangeFrom(String address) {
    return MockServerWebExchange.from(
        MockServerHttpRequest.get("/lists")
            .header(HttpHeaders.HOST, "gateway.test")
            .remoteAddress(new InetSocketAddress(address, 41000)));
  }

  private static GatewaySecurityProperties properties() {
    return new GatewaySecurityProperties(
        "http://test-oidc.local/realms/shopping-list-test",
        "sub",
        "roles",
        Duration.ofMinutes(30),
        10_000);
  }
}
