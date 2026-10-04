package dev.ivborrezo.shoppinglist.product.service.product;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.ivborrezo.shoppinglist.product.service.product.repository.BaseProductRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
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
 * Test de integración del endpoint {@code DELETE /base-products/{id}}.
 *
 * <p>Verifica el borrado físico de un producto base y sus traducciones en cascada (por {@code ON
 * DELETE CASCADE} en la FK de {@code base_product_translation}), y el {@code 404} para productos
 * inexistentes o ya eliminados.
 */
@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@AutoConfigureMockMvc
@TestConstructor(autowireMode = AutowireMode.ALL)
@Transactional
@Testcontainers
class BaseProductDeletionIntegrationIT {

  @Container @ServiceConnection
  static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

  private final MockMvc mockMvc;

  private final BaseProductRepository baseProductRepository;

  BaseProductDeletionIntegrationIT(MockMvc mockMvc, BaseProductRepository baseProductRepository) {
    this.mockMvc = mockMvc;
    this.baseProductRepository = baseProductRepository;
  }

  /** Borra un producto base existente y devuelve 204. */
  @Test
  void deleteBaseProduct_existingProduct_returns204() throws Exception {
    mockMvc
        .perform(delete("/base-products/" + baseProductPublicId(5L)))
        .andExpect(status().isNoContent());
  }

  /** Devuelve 404 al intentar borrar un producto ya eliminado. */
  @Test
  void deleteBaseProduct_alreadyDeletedProduct_returns404() throws Exception {
    UUID publicId = baseProductPublicId(5L);
    mockMvc.perform(delete("/base-products/" + publicId)).andExpect(status().isNoContent());

    mockMvc.perform(delete("/base-products/" + publicId)).andExpect(status().isNotFound());
  }

  /** Devuelve 404 cuando el identificador de producto base no existe. */
  @Test
  void deleteBaseProduct_withNonExistentId_returns404() throws Exception {
    mockMvc.perform(delete("/base-products/" + UUID.randomUUID())).andExpect(status().isNotFound());
  }

  /**
   * Resuelve el identificador público del producto base con el identificador interno indicado.
   *
   * @param id identificador interno del producto base del seed
   * @return identificador público del producto base
   */
  private UUID baseProductPublicId(Long id) {
    return baseProductRepository.findById(id).orElseThrow().getPublicId();
  }
}
