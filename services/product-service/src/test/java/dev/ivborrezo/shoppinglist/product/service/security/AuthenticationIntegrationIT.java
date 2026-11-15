package dev.ivborrezo.shoppinglist.product.service.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.ivborrezo.shoppinglist.testsupport.TestJwksServer;
import dev.ivborrezo.shoppinglist.testsupport.TestJwtFactory;
import dev.ivborrezo.shoppinglist.testsupport.TestRsaKey;
import dev.ivborrezo.shoppinglist.testsupport.TestUsers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.TestConstructor.AutowireMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Test de integración de la autenticación JWT con el decodificador real del servicio.
 *
 * <p>Sirve el JWKS de test desde WireMock y valida el rechazo {@code 401 UNAUTHENTICATED} ante
 * credenciales ausentes, tokens expirados, firmas inválidas, emisores desconocidos, sujetos no UUID
 * y tokens malformados, además del acceso público al endpoint de salud.
 */
@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@AutoConfigureMockMvc
@TestConstructor(autowireMode = AutowireMode.ALL)
@Testcontainers
class AuthenticationIntegrationIT {

  @Container @ServiceConnection
  static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

  private static final TestJwksServer JWKS = TestJwksServer.start();

  private final MockMvc mockMvc;

  AuthenticationIntegrationIT(MockMvc mockMvc) {
    this.mockMvc = mockMvc;
  }

  @DynamicPropertySource
  static void jwtProperties(DynamicPropertyRegistry registry) {
    registry.add(
        "spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> TestUsers.TEST_ISSUER);
    registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", JWKS::jwksUri);
  }

  @AfterAll
  static void stopJwks() {
    JWKS.stop();
  }

  /** Rechaza con 401 las peticiones sin cabecera de autorización. */
  @Test
  void requestWithoutCredentials_returns401Unauthenticated() throws Exception {
    assertUnauthenticated(mockMvc.perform(get("/categories")));
  }

  /** Rechaza con 401 un token expirado. */
  @Test
  void expiredToken_returns401Unauthenticated() throws Exception {
    String token = TestJwtFactory.token().subject(TestUsers.USER_1).expired().build();

    assertUnauthenticated(withToken(token));
  }

  /** Rechaza con 401 un token firmado con una clave distinta a la publicada. */
  @Test
  void invalidSignature_returns401Unauthenticated() throws Exception {
    String token = TestJwtFactory.token().signedWith(TestRsaKey.alternative()).build();

    assertUnauthenticated(withToken(token));
  }

  /** Rechaza con 401 un token con un emisor no reconocido. */
  @Test
  void unknownIssuer_returns401Unauthenticated() throws Exception {
    String token =
        TestJwtFactory.token().issuer("http://unknown-issuer.local/realms/other").build();

    assertUnauthenticated(withToken(token));
  }

  /** Rechaza con 401 un token cuyo claim de sujeto no es un UUID válido. */
  @Test
  void nonUuidSubject_returns401Unauthenticated() throws Exception {
    String token = TestJwtFactory.token().subject("not-a-uuid").build();

    assertUnauthenticated(withToken(token));
  }

  /** Rechaza con 401 un token sintácticamente malformado. */
  @Test
  void malformedToken_returns401Unauthenticated() throws Exception {
    assertUnauthenticated(withToken(TestJwtFactory.malformed()));
  }

  /** No redirige a la página de login ante credenciales ausentes. */
  @Test
  void requestWithoutCredentials_doesNotRedirect() throws Exception {
    mockMvc.perform(get("/categories")).andExpect(status().isUnauthorized());
  }

  /** Permite el acceso sin credenciales al endpoint de salud. */
  @Test
  void health_isPubliclyAccessible() throws Exception {
    mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
  }

  private ResultActions withToken(String token) throws Exception {
    return mockMvc.perform(get("/categories").header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
  }

  private void assertUnauthenticated(ResultActions result) throws Exception {
    result
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
  }
}
