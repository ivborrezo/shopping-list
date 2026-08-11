package dev.ivborrezo.shoppinglist.product.service.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.ivborrezo.shoppinglist.product.service.product.entity.UserProduct;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Test de integración del endpoint {@code DELETE /user-products/{id}}.
 *
 * <p>Verifica el borrado físico de un producto de usuario con propietario coincidente, el rechazo
 * con propietario no coincidente, la exigencia del query param {@code ownerId} y el {@code 404}
 * para productos inexistentes.
 */
@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@AutoConfigureMockMvc
@AutoConfigureTestEntityManager
@TestConstructor(autowireMode = AutowireMode.ALL)
@Transactional
@Testcontainers
class UserProductDeletionIntegrationIT {

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

  private static final UUID OWNER_ID = UUID.fromString("aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee");

  private static final UUID OTHER_OWNER_ID =
      UUID.fromString("aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeef");

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
        .perform(delete("/user-products/" + product.getId()).param("ownerId", OWNER_ID.toString()))
        .andExpect(status().isNoContent());

    assertThat(entityManager.find(UserProduct.class, product.getId())).isNull();
  }

  /**
   * Rechaza con 403 el borrado cuando el {@code ownerId} del query param no coincide con el del
   * producto.
   */
  @Test
  void deleteUserProduct_withMismatchedOwner_returns403() throws Exception {
    UserProduct product = buildProduct(OWNER_ID);
    persist(product);

    mockMvc
        .perform(
            delete("/user-products/" + product.getId()).param("ownerId", OTHER_OWNER_ID.toString()))
        .andExpect(status().isForbidden());
  }

  /** Devuelve 400 cuando falta el query param {@code ownerId}. */
  @Test
  void deleteUserProduct_withoutOwnerId_returns400() throws Exception {
    UserProduct product = buildProduct(OWNER_ID);
    persist(product);

    mockMvc.perform(delete("/user-products/" + product.getId())).andExpect(status().isBadRequest());
  }

  /** Devuelve 404 cuando el identificador de producto de usuario no existe. */
  @Test
  void deleteUserProduct_nonexistentId_returns404() throws Exception {
    mockMvc
        .perform(delete("/user-products/99999").param("ownerId", OWNER_ID.toString()))
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
    product.setDefaultUnit("UNIT");
    product.setCaloriesPer("G");
    product.setShareWithListMembers(false);
    product.setShareWithFriends(false);
    product.setIsActive(true);
    return product;
  }
}
