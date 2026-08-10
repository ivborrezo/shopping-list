package dev.ivborrezo.shoppinglist.product.service.product;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
 * Test de integración del endpoint {@code DELETE /base-products/{id}}.
 *
 * <p>Verifica el borrado físico de un producto base y sus traducciones en cascada (por {@code ON
 * DELETE CASCADE} en la FK de {@code base_product_translation}), y el {@code 404} para productos
 * inexistentes o ya eliminados.
 */
@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@AutoConfigureMockMvc
@AutoConfigureTestEntityManager
@TestConstructor(autowireMode = AutowireMode.ALL)
@Transactional
@Testcontainers
class BaseProductDeletionIntegrationIT {

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

  private final MockMvc mockMvc;

  private final TestEntityManager entityManager;

  BaseProductDeletionIntegrationIT(MockMvc mockMvc, TestEntityManager entityManager) {
    this.mockMvc = mockMvc;
    this.entityManager = entityManager;
  }

  /** Borra un producto base existente y devuelve 204. */
  @Test
  void deleteBaseProduct_existingProduct_returns204() throws Exception {
    mockMvc.perform(delete("/base-products/5")).andExpect(status().isNoContent());
  }

  /** Devuelve 404 al intentar borrar un producto ya eliminado. */
  @Test
  void deleteBaseProduct_alreadyDeletedProduct_returns404() throws Exception {
    mockMvc.perform(delete("/base-products/5")).andExpect(status().isNoContent());

    mockMvc.perform(delete("/base-products/5")).andExpect(status().isNotFound());
  }

  /** Devuelve 404 cuando el identificador de producto base no existe. */
  @Test
  void deleteBaseProduct_withNonExistentId_returns404() throws Exception {
    mockMvc.perform(delete("/base-products/9999")).andExpect(status().isNotFound());
  }
}
