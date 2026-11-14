package dev.ivborrezo.shoppinglist.product.service.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.ivborrezo.shoppinglist.testsupport.TestJwksServer;
import dev.ivborrezo.shoppinglist.testsupport.TestJwtFactory;
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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Test de integración de la configurabilidad de los claims de identidad y roles.
 *
 * <p>Sobrescribe los nombres de claim por configuración y comprueba que un token con {@code
 * user_id} y {@code realm_roles} autentica al usuario y autoriza una operación de administración.
 */
@SpringBootTest(
    webEnvironment = WebEnvironment.MOCK,
    properties = {"security.jwt.principal-claim=user_id", "security.jwt.roles-claim=realm_roles"})
@AutoConfigureMockMvc
@TestConstructor(autowireMode = AutowireMode.ALL)
@Testcontainers
class ConfigurableClaimsIntegrationIT {

  @Container @ServiceConnection
  static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

  private static final TestJwksServer JWKS = TestJwksServer.start();

  private static final String CATEGORY_BODY =
      """
      {
        "code": "configurable_claims",
        "isActive": true,
        "translations": [{"locale": "es", "name": "Claims configurables"}]
      }
      """;

  private final MockMvc mockMvc;

  ConfigurableClaimsIntegrationIT(MockMvc mockMvc) {
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

  /**
   * Autentica con el claim {@code user_id} y autoriza con el claim {@code realm_roles}.
   *
   * @throws Exception si la petición MockMvc falla
   */
  @Test
  void configuredClaims_authenticateAndAuthorizeAdminOperation() throws Exception {
    String token =
        TestJwtFactory.token()
            .subject("not-a-uuid")
            .claim("user_id", TestUsers.ADMIN.toString())
            .rolesClaim("realm_roles")
            .roles("ADMIN")
            .build();

    mockMvc
        .perform(
            post("/categories")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(CATEGORY_BODY))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.code").value("configurable_claims"));
  }

  /**
   * Rechaza con 401 un token que no incluye el claim de identidad configurado.
   *
   * @throws Exception si la petición MockMvc falla
   */
  @Test
  void missingConfiguredPrincipalClaim_returns401() throws Exception {
    String token = TestJwtFactory.token().subject(TestUsers.USER_1).build();

    mockMvc
        .perform(get("/categories").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
  }
}
