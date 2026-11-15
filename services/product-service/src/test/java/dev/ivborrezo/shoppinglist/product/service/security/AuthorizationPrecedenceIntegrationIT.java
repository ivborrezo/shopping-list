package dev.ivborrezo.shoppinglist.product.service.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.ivborrezo.shoppinglist.product.service.common.CaloriesPerEnum;
import dev.ivborrezo.shoppinglist.product.service.common.UnitEnum;
import dev.ivborrezo.shoppinglist.product.service.product.entity.UserProduct;
import dev.ivborrezo.shoppinglist.testsupport.TestAuth;
import dev.ivborrezo.shoppinglist.testsupport.TestUsers;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.jpa.test.autoconfigure.AutoConfigureTestEntityManager;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.TestConstructor.AutowireMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Test de integración de la precedencia de las comprobaciones de seguridad.
 *
 * <p>Verifica el orden {@code 401 UNAUTHENTICATED} → {@code 403 ACCESS_DENIED} por rol → {@code
 * 404} de existencia → {@code 403 OWNER_MISMATCH} de propiedad.
 */
@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@AutoConfigureMockMvc
@AutoConfigureTestEntityManager
@TestConstructor(autowireMode = AutowireMode.ALL)
@Transactional
@Testcontainers
class AuthorizationPrecedenceIntegrationIT {

  @Container @ServiceConnection
  static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

  private final MockMvc mockMvc;

  private final TestEntityManager entityManager;

  AuthorizationPrecedenceIntegrationIT(MockMvc mockMvc, TestEntityManager entityManager) {
    this.mockMvc = mockMvc;
    this.entityManager = entityManager;
  }

  /**
   * Sin credenciales, cualquier endpoint de negocio responde 401 antes que cualquier otra regla.
   */
  @Test
  void withoutCredentials_returns401BeforeAnyOtherCheck() throws Exception {
    assertCode(mockMvc.perform(get("/user-products/" + UUID.randomUUID())), 401, "UNAUTHENTICATED");
  }

  /**
   * Un usuario sin rol ADMIN recibe 403 antes de comprobar si el producto base existe.
   *
   * @throws Exception si la petición MockMvc falla
   */
  @Test
  void userRoleOnCatalogMutation_returns403BeforeExistenceCheck() throws Exception {
    assertCode(
        mockMvc.perform(
            patch("/base-products/" + UUID.randomUUID())
                .with(TestAuth.user1())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"isActive\": false}")),
        403,
        "ACCESS_DENIED");
  }

  /**
   * Con autenticación válida, un recurso de usuario inexistente responde 404 antes que 403.
   *
   * @throws Exception si la petición MockMvc falla
   */
  @Test
  void authenticatedUserOnMissingResource_returns404() throws Exception {
    assertCode(
        mockMvc.perform(get("/user-products/" + UUID.randomUUID()).with(TestAuth.user1())),
        404,
        "USER_PRODUCT_NOT_FOUND");
  }

  /**
   * Con un recurso existente de otro usuario, la respuesta es 403 OWNER_MISMATCH.
   *
   * @throws Exception si la petición MockMvc falla
   */
  @Test
  void authenticatedUserOnForeignResource_returns403OwnerMismatch() throws Exception {
    UserProduct product = new UserProduct();
    product.setOwnerId(TestUsers.USER_1);
    product.setName("Producto ajeno");
    product.setDefaultUnit(UnitEnum.UNIT);
    product.setCaloriesPer(CaloriesPerEnum.G);
    product.setShareWithListMembers(false);
    product.setShareWithFriends(false);
    product.setIsActive(true);
    entityManager.persistAndFlush(product);

    assertCode(
        mockMvc.perform(get("/user-products/" + product.getPublicId()).with(TestAuth.user2())),
        403,
        "OWNER_MISMATCH");
  }

  private void assertCode(ResultActions result, int status, String code) throws Exception {
    result
        .andExpect(status().is(status))
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value(code));
  }
}
