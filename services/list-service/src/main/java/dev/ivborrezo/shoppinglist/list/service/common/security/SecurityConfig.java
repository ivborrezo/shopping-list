package dev.ivborrezo.shoppinglist.list.service.common.security;

import dev.ivborrezo.shoppinglist.list.service.common.ErrorCode;
import dev.ivborrezo.shoppinglist.list.service.common.web.UserIdMdcFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import tools.jackson.databind.ObjectMapper;

/**
 * Configuración de seguridad del servicio como OAuth2 Resource Server sin estado.
 *
 * <p>Valida los JWT contra el emisor y el JWKS configurados, convierte el claim de roles en
 * autoridades {@code ROLE_<ROL>}, deja público el endpoint de salud, exige autenticación en el
 * resto de endpoints y responde los errores de autenticación y autorización con el formato {@code
 * ProblemDetail} del contrato. Registra además el resolver de {@link CurrentUserId @CurrentUserId}
 * y el filtro que publica el usuario en el MDC.
 */
@Configuration
@EnableConfigurationProperties(SecurityJwtProperties.class)
public class SecurityConfig implements WebMvcConfigurer {

  private static final String CORRELATION_HEADER = "X-Correlation-Id";

  private static final String CORRELATION_MDC_KEY = "correlationId";

  private final SecurityJwtProperties properties;

  private final ObjectMapper objectMapper;

  private final String issuerUri;

  private final String jwkSetUri;

  /**
   * Construye la configuración con las propiedades de claims y los metadatos OIDC del perfil.
   *
   * @param properties propiedades de los claims de identificador de usuario y roles
   * @param objectMapper mapper con el que se serializan los errores {@code ProblemDetail}
   * @param issuerUri emisor esperado de los tokens
   * @param jwkSetUri URL del conjunto de claves públicas del proveedor OIDC
   */
  public SecurityConfig(
      SecurityJwtProperties properties,
      ObjectMapper objectMapper,
      @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuerUri,
      @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String jwkSetUri) {
    this.properties = properties;
    this.objectMapper = objectMapper;
    this.issuerUri = issuerUri;
    this.jwkSetUri = jwkSetUri;
  }

  /**
   * Define la cadena de filtros sin estado, las reglas de autorización y los manejadores de error.
   *
   * @param http constructor de la cadena de seguridad
   * @return cadena de filtros de seguridad
   * @throws Exception si la configuración de seguridad no puede construirse
   */
  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http.csrf(csrf -> csrf.disable())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/actuator/health", "/actuator/health/**")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .oauth2ResourceServer(
            oauth2 ->
                oauth2
                    .authenticationEntryPoint(
                        (request, response, exception) ->
                            writeProblem(
                                request,
                                response,
                                ErrorCode.UNAUTHENTICATED,
                                "Authentication required"))
                    .accessDeniedHandler(
                        (request, response, exception) ->
                            writeProblem(
                                request, response, ErrorCode.ACCESS_DENIED, "Access denied"))
                    .jwt(
                        jwt ->
                            jwt.decoder(jwtDecoder())
                                .jwtAuthenticationConverter(jwtAuthenticationConverter())))
        .addFilterAfter(new UserIdMdcFilter(properties), BearerTokenAuthenticationFilter.class);
    return http.build();
  }

  /**
   * Construye el decodificador JWT de forma perezosa desde el JWKS configurado.
   *
   * <p>No realiza llamadas HTTP de discovery al arrancar el contexto: las claves se descargan al
   * validar el primer token. El validador comprueba emisor, expiración y que el claim de
   * identificador de usuario sea un UUID válido.
   *
   * @return decodificador de JWT con los validadores del servicio
   */
  @Bean
  public JwtDecoder jwtDecoder() {
    NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
    decoder.setJwtValidator(
        new DelegatingOAuth2TokenValidator<>(
            JwtValidators.createDefaultWithIssuer(issuerUri),
            new SubjectUuidJwtValidator(properties.getPrincipalClaim())));
    return decoder;
  }

  /**
   * Convierte el claim de roles configurado en autoridades {@code ROLE_<ROL>}.
   *
   * @return conversor de autenticaciones JWT del servicio
   */
  @Bean
  public JwtAuthenticationConverter jwtAuthenticationConverter() {
    JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
    authoritiesConverter.setAuthoritiesClaimName(properties.getRolesClaim());
    authoritiesConverter.setAuthorityPrefix("ROLE_");
    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
    return converter;
  }

  @Override
  public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
    resolvers.add(new CurrentUserIdArgumentResolver(properties));
  }

  private void writeProblem(
      HttpServletRequest request, HttpServletResponse response, ErrorCode errorCode, String detail)
      throws IOException {
    response.setStatus(errorCode.getHttpStatus());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    Optional.ofNullable(MDC.get(CORRELATION_MDC_KEY))
        .ifPresent(correlationId -> response.setHeader(CORRELATION_HEADER, correlationId));

    Map<String, Object> body = new LinkedHashMap<>();
    body.put("type", "about:blank");
    body.put("title", errorCode.getTitle());
    body.put("status", errorCode.getHttpStatus());
    body.put("detail", detail);
    body.put("instance", request.getRequestURI());
    body.put("code", errorCode.name());
    objectMapper.writeValue(response.getOutputStream(), body);
  }
}
