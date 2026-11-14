package dev.ivborrezo.shoppinglist.product.service.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.TestConstructor.AutowireMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Test de integración del endpoint {@code DELETE /user-products/{id}}.
 *
 * <p>Verifica el borrado físico de un producto de usuario propio, el rechazo cuando pertenece a
 * otro usuario, el borrado de productos inactivos por su propietario y el {@code 404} para
 * productos inexistentes.
 */
@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@AutoConfigureMockMvc
@AutoConfigureTestEntityManager
@TestConstructor(autowireMode = AutowireMode.ALL)
@Transactional
@Testcontainers
class UserProductDeletionIntegrationIT {

  @Container @ServiceConnection
  static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

  private static final UUID OWNER_ID = TestUsers.USER_1;

  private final MockMvc mockMvc;

  private final TestEntityManager entityManager;

  UserProductDeletionIntegrationIT(MockMvc mockMvc, TestEntityManager entityManager) {
    this.mockMvc = mockMvc;
    this.entityManager = entityManager;
  }

  /** Borra un producto de usuario del propietario indicado y devuelve 204. */
  @Test
  void deleteUserProduct_withMatchingOwner_returns204() throws Exception {
    UserProduct product = buildProduct(OWNER_ID);
    persist(product);

    mockMvc
        .perform(delete("/user-products/" + product.getPublicId()).with(TestAuth.user1()))
        .andExpect(status().isNoContent());

    assertThat(entityManager.find(UserProduct.class, product.getId())).isNull();
  }

  /** Rechaza con 403 el borrado cuando el producto pertenece a otro usuario. */
  @Test
  void deleteUserProduct_withMismatchedOwner_returns403() throws Exception {
    UserProduct product = buildProduct(OWNER_ID);
    persist(product);

    mockMvc
        .perform(delete("/user-products/" + product.getPublicId()).with(TestAuth.user2()))
        .andExpect(status().isForbidden());
  }

  /** Permite al propietario borrar un producto inactivo propio. */
  @Test
  void deleteUserProduct_inactiveProductByOwner_returns204() throws Exception {
    UserProduct product = buildProduct(OWNER_ID);
    product.setIsActive(false);
    persist(product);

    mockMvc
        .perform(delete("/user-products/" + product.getPublicId()).with(TestAuth.user1()))
        .andExpect(status().isNoContent());
  }

  /** Devuelve 404 cuando el identificador de producto de usuario no existe. */
  @Test
  void deleteUserProduct_nonexistentId_returns404() throws Exception {
    mockMvc
        .perform(delete("/user-products/" + UUID.randomUUID()).with(TestAuth.user1()))
        .andExpect(status().isNotFound());
  }

  /**
   * Persiste el producto indicado dentro de la transacción del test y fuerza el {@code flush} para
   * que JPA asigne el identificador generado.
   *
   * @param product producto de usuario a insertar
   */
  private void persist(UserProduct product) {
    entityManager.persist(product);
    entityManager.flush();
  }

  /**
   * Construye un producto de usuario activo con los campos mínimos para los tests.
   *
   * @param ownerId propietario del producto
   * @return entidad {@link UserProduct} con los valores indicados
   */
  private UserProduct buildProduct(UUID ownerId) {
    UserProduct product = new UserProduct();
    product.setOwnerId(ownerId);
    product.setName("Producto de prueba");
    product.setDescription("Descripción de prueba");
    product.setCategoryId(1L);
    product.setDefaultUnit(UnitEnum.UNIT);
    product.setCaloriesPer(CaloriesPerEnum.G);
    product.setShareWithListMembers(false);
    product.setShareWithFriends(false);
    product.setIsActive(true);
    return product;
  }
}
