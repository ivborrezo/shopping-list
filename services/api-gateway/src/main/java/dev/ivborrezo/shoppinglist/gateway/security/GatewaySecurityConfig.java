package dev.ivborrezo.shoppinglist.gateway.security;

import dev.ivborrezo.shoppinglist.gateway.error.ProblemDetailErrorWriter;
import dev.ivborrezo.shoppinglist.gateway.ratelimit.RateLimitExceededException;
import dev.ivborrezo.shoppinglist.gateway.ratelimit.RateLimitKeyResolver;
import dev.ivborrezo.shoppinglist.gateway.ratelimit.RateLimitWebFilter;
import dev.ivborrezo.shoppinglist.gateway.ratelimit.RemoteAddressKeyResolver;
import dev.ivborrezo.shoppinglist.gateway.ratelimit.TokenBucketRateLimiter;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.client.endpoint.WebClientReactiveAuthorizationCodeTokenResponseClient;
import org.springframework.security.oauth2.client.oidc.authentication.OidcAuthorizationCodeReactiveAuthenticationManager;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcReactiveOAuth2UserService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoderFactory;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.logout.HttpStatusReturningServerLogoutSuccessHandler;
import org.springframework.security.web.server.authentication.logout.WebSessionServerLogoutHandler;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.security.web.server.context.ServerSecurityContextRepository;
import org.springframework.security.web.server.context.WebSessionServerSecurityContextRepository;
import org.springframework.security.web.server.csrf.CookieServerCsrfTokenRepository;
import org.springframework.security.web.server.csrf.CsrfToken;
import org.springframework.security.web.server.csrf.ServerCsrfTokenRequestAttributeHandler;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.session.CookieWebSessionIdResolver;
import org.springframework.web.server.session.DefaultWebSessionManager;
import org.springframework.web.server.session.InMemoryWebSessionStore;
import org.springframework.web.server.session.WebSessionManager;
import reactor.core.publisher.Mono;

/**
 * Configuración de seguridad dual del borde.
 *
 * <p>Define una cadena {@code Bearer} sin estado para clientes API y una cadena de sesión OAuth2
 * BFF para navegadores. Ambas validan la firma, la expiración, el emisor y el formato UUID del
 * sujeto, publican el identificador del usuario y aplican el rate limiting después de autenticar.
 * Los errores de autenticación y autorización se escriben con el formato del contrato a través de
 * {@link ProblemDetailErrorWriter}. El almacén de sesiones en memoria se acota por inactividad y
 * por número de sesiones concurrentes.
 */
@Configuration
@EnableConfigurationProperties(GatewaySecurityProperties.class)
public class GatewaySecurityConfig {

  private static final String BEARER_PREFIX = "Bearer ";

  private static final String SESSION_COOKIE = "SESSION";

  private static final WebFilter CSRF_TOKEN_EMISSION_FILTER =
      (exchange, chain) -> {
        Object attribute = exchange.getAttribute(CsrfToken.class.getName());
        if (attribute instanceof Mono<?> token) {
          return token.then(chain.filter(exchange));
        }
        return chain.filter(exchange);
      };

  private final GatewaySecurityProperties properties;

  private final ProblemDetailErrorWriter errorWriter;

  private final TokenBucketRateLimiter rateLimiter;

  private final RemoteAddressKeyResolver ipKeyResolver;

  private final UserIdMdcWebFilter userIdMdcWebFilter;

  private final RateLimitWebFilter rateLimitWebFilter;

  private final String jwkSetUri;

  /**
   * Inyecta las dependencias del borde y los metadatos OIDC del perfil.
   *
   * @param properties configuración de identidad y sesión
   * @param errorWriter escritor único de los errores del borde
   * @param rateLimiter limitador compartido del borde
   * @param keyResolver resolución de la identidad de rate limiting
   * @param ipKeyResolver resolución de la clave de IP para los rechazos de autenticación
   * @param configuredJwkSetUri URL del conjunto de claves públicas del proveedor OIDC
   */
  public GatewaySecurityConfig(
      GatewaySecurityProperties properties,
      ProblemDetailErrorWriter errorWriter,
      TokenBucketRateLimiter rateLimiter,
      RateLimitKeyResolver keyResolver,
      RemoteAddressKeyResolver ipKeyResolver,
      @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri:}")
          String configuredJwkSetUri) {
    this.properties = properties;
    this.errorWriter = errorWriter;
    this.rateLimiter = rateLimiter;
    this.ipKeyResolver = ipKeyResolver;
    this.userIdMdcWebFilter = new UserIdMdcWebFilter(properties.principalClaim(), errorWriter);
    this.rateLimitWebFilter = new RateLimitWebFilter(rateLimiter, keyResolver);
    this.jwkSetUri = configuredJwkSetUri;
  }

  /**
   * Crea el gestor de sesiones en memoria acotado por inactividad y número de sesiones.
   *
   * @param properties configuración de identidad y sesión
   * @param clock reloj con el que se mide la inactividad de la sesión
   * @return gestor de sesiones del borde
   */
  public static DefaultWebSessionManager createWebSessionManager(
      GatewaySecurityProperties properties, Clock clock) {
    InMemoryWebSessionStore store = new InMemoryWebSessionStore();
    store.setClock(clock);
    store.setDefaultMaxIdleTime(properties.sessionTtl());
    store.setMaxSessions(properties.maxSessions());

    CookieWebSessionIdResolver sessionIdResolver = new CookieWebSessionIdResolver();
    sessionIdResolver.setCookieName(SESSION_COOKIE);
    sessionIdResolver.addCookieInitializer(cookie -> cookie.httpOnly(true).sameSite("Lax"));

    DefaultWebSessionManager sessionManager = new DefaultWebSessionManager();
    sessionManager.setSessionStore(store);
    sessionManager.setSessionIdResolver(sessionIdResolver);
    return sessionManager;
  }

  @Bean
  ServerSecurityContextRepository securityContextRepository() {
    return new WebSessionServerSecurityContextRepository();
  }

  @Bean
  WebSessionManager webSessionManager(Clock clock) {
    return createWebSessionManager(properties, clock);
  }

  @Bean
  ReactiveJwtDecoder jwtDecoder() {
    NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withJwkSetUri(jwkSetUri).build();
    decoder.setJwtValidator(
        new DelegatingOAuth2TokenValidator<>(
            JwtValidators.createDefaultWithIssuer(properties.issuerUri()),
            new SubjectUuidJwtValidator(properties.principalClaim())));
    return decoder;
  }

  @Bean
  ReactiveJwtDecoderFactory<ClientRegistration> idTokenDecoderFactory() {
    return registration -> {
      NimbusReactiveJwtDecoder decoder =
          NimbusReactiveJwtDecoder.withJwkSetUri(registration.getProviderDetails().getJwkSetUri())
              .build();
      decoder.setJwtValidator(idTokenValidator(registration));
      return decoder;
    };
  }

  @Bean
  @Order(1)
  SecurityWebFilterChain bearerSecurityWebFilterChain(
      ServerHttpSecurity http, ReactiveJwtDecoder jwtDecoder) {
    return http.securityMatcher(this::isBearerRequest)
        .csrf(ServerHttpSecurity.CsrfSpec::disable)
        .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
        .authorizeExchange(exchanges -> exchanges.anyExchange().authenticated())
        .exceptionHandling(
            handling ->
                handling
                    .authenticationEntryPoint(this::writeAuthenticationFailure)
                    .accessDeniedHandler(this::writeAccessDenied))
        .oauth2ResourceServer(
            oauth2 ->
                oauth2
                    .authenticationEntryPoint(this::writeAuthenticationFailure)
                    .accessDeniedHandler(this::writeAccessDenied)
                    .jwt(jwt -> jwt.jwtDecoder(jwtDecoder)))
        .addFilterAfter(userIdMdcWebFilter, SecurityWebFiltersOrder.AUTHENTICATION)
        .addFilterAfter(rateLimitWebFilter, SecurityWebFiltersOrder.AUTHENTICATION)
        .build();
  }

  @Bean
  @Order(2)
  SecurityWebFilterChain sessionSecurityWebFilterChain(
      ServerHttpSecurity http,
      ReactiveJwtDecoderFactory<ClientRegistration> idTokenDecoderFactory,
      ServerSecurityContextRepository securityContextRepository) {
    return http.csrf(
            csrf ->
                csrf.csrfTokenRepository(CookieServerCsrfTokenRepository.withHttpOnlyFalse())
                    .csrfTokenRequestHandler(new ServerCsrfTokenRequestAttributeHandler())
                    .requireCsrfProtectionMatcher(
                        exchange -> requiresCsrfProtection(exchange, securityContextRepository))
                    .accessDeniedHandler(this::writeAccessDenied))
        .securityContextRepository(securityContextRepository)
        .authorizeExchange(
            exchanges ->
                exchanges
                    .pathMatchers(HttpMethod.OPTIONS)
                    .permitAll()
                    .pathMatchers("/actuator/health", "/actuator/health/**")
                    .permitAll()
                    .pathMatchers("/oauth2/authorization/**", "/login/oauth2/code/**")
                    .permitAll()
                    .anyExchange()
                    .authenticated())
        .exceptionHandling(
            handling ->
                handling
                    .authenticationEntryPoint(this::writeAuthenticationFailure)
                    .accessDeniedHandler(this::writeAccessDenied))
        .oauth2Login(
            login ->
                login
                    .authenticationManager(oidcAuthenticationManager(idTokenDecoderFactory))
                    .authenticationFailureHandler(
                        (exchange, exception) ->
                            writeAuthenticationFailure(exchange.getExchange(), exception)))
        .logout(
            logout ->
                logout
                    .requiresLogout(
                        ServerWebExchangeMatchers.pathMatchers(HttpMethod.POST, "/logout"))
                    .logoutHandler(new WebSessionServerLogoutHandler())
                    .logoutSuccessHandler(
                        new HttpStatusReturningServerLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
        .addFilterAfter(CSRF_TOKEN_EMISSION_FILTER, SecurityWebFiltersOrder.CSRF)
        .addFilterAfter(
            userIdMdcWebFilter, SecurityWebFiltersOrder.SECURITY_CONTEXT_SERVER_WEB_EXCHANGE)
        .addFilterAfter(
            rateLimitWebFilter, SecurityWebFiltersOrder.SECURITY_CONTEXT_SERVER_WEB_EXCHANGE)
        .build();
  }

  private OAuth2TokenValidator<Jwt> idTokenValidator(ClientRegistration registration) {
    JwtClaimValidator<List<String>> audienceValidator =
        new JwtClaimValidator<>(
            JwtClaimNames.AUD,
            audiences -> audiences != null && audiences.contains(registration.getClientId()));
    return new DelegatingOAuth2TokenValidator<>(
        JwtValidators.createDefaultWithIssuer(properties.issuerUri()),
        audienceValidator,
        new SubjectUuidJwtValidator(properties.principalClaim()));
  }

  private ReactiveAuthenticationManager oidcAuthenticationManager(
      ReactiveJwtDecoderFactory<ClientRegistration> idTokenDecoderFactory) {
    OidcAuthorizationCodeReactiveAuthenticationManager authenticationManager =
        new OidcAuthorizationCodeReactiveAuthenticationManager(
            new WebClientReactiveAuthorizationCodeTokenResponseClient(),
            new OidcReactiveOAuth2UserService());
    authenticationManager.setJwtDecoderFactory(idTokenDecoderFactory);
    return authenticationManager;
  }

  private Mono<ServerWebExchangeMatcher.MatchResult> isBearerRequest(ServerWebExchange exchange) {
    String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
    boolean bearer =
        authorization != null && authorization.regionMatches(true, 0, BEARER_PREFIX, 0, 7);
    return bearer
        ? ServerWebExchangeMatcher.MatchResult.match()
        : ServerWebExchangeMatcher.MatchResult.notMatch();
  }

  private Mono<ServerWebExchangeMatcher.MatchResult> requiresCsrfProtection(
      ServerWebExchange exchange, ServerSecurityContextRepository securityContextRepository) {
    HttpMethod method = exchange.getRequest().getMethod();
    if (method != HttpMethod.POST && method != HttpMethod.PATCH && method != HttpMethod.DELETE) {
      return ServerWebExchangeMatcher.MatchResult.notMatch();
    }
    return securityContextRepository
        .load(exchange)
        .map(SecurityContext::getAuthentication)
        .filter(Authentication::isAuthenticated)
        .hasElement()
        .flatMap(
            authenticated ->
                authenticated
                    ? ServerWebExchangeMatcher.MatchResult.match()
                    : ServerWebExchangeMatcher.MatchResult.notMatch());
  }

  private Mono<Void> writeAuthenticationFailure(
      ServerWebExchange exchange, AuthenticationException error) {
    if (Boolean.TRUE.equals(
        exchange.getAttribute(RateLimitWebFilter.RATE_LIMIT_CHECKED_ATTRIBUTE))) {
      return errorWriter.handle(exchange, error);
    }
    exchange.getAttributes().put(RateLimitWebFilter.RATE_LIMIT_CHECKED_ATTRIBUTE, Boolean.TRUE);
    Optional<Duration> retryAfter = rateLimiter.consume(ipKeyResolver.resolveIpKey(exchange));
    return retryAfter.isPresent()
        ? errorWriter.handle(exchange, new RateLimitExceededException(retryAfter.get()))
        : errorWriter.handle(exchange, error);
  }

  private Mono<Void> writeAccessDenied(ServerWebExchange exchange, AccessDeniedException error) {
    return errorWriter.handle(exchange, error);
  }
}
