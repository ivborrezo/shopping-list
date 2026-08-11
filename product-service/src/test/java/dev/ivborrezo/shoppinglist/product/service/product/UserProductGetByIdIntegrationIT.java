package dev.ivborrezo.shoppinglist.product.service.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.ivborrezo.shoppinglist.product.service.common.CaloriesPerEnum;
import dev.ivborrezo.shoppinglist.product.service.common.UnitEnum;
import dev.ivborrezo.shoppinglist.product.service.product.dto.UserProductResponseDto;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;

/**
 * Test de integración del endpoint {@code GET /user-products/{id}}.
 *
 * <p>Inserta los productos de usuario vía {@link TestEntityManager} dentro de la transacción del
 * test y verifica la recuperación de un producto concreto por su identificador y el {@code 404}
 * para productos inexistentes o inactivos.
 */
@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@AutoConfigureMockMvc
@AutoConfigureTestEntityManager
@TestConstructor(autowireMode = AutowireMode.ALL)
@Transactional
@Testcontainers
class UserProductGetByIdIntegrationIT {

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

  private static final UUID OWNER_A = UUID.fromString("aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeee1");

  private final MockMvc mockMvc;

  private final ObjectMapper objectMapper;

  private final TestEntityManager entityManager;

  UserProductGetByIdIntegrationIT(
      MockMvc mockMvc, ObjectMapper objectMapper, TestEntityManager entityManager) {
    this.mockMvc = mockMvc;
    this.objectMapper = objectMapper;
    this.entityManager = entityManager;
  }

  /** Devuelve el producto activo con todos los campos mapeados al DTO. */
  @Test
  void getById_existingAndActive_returns200AndFields() throws Exception {
    UserProduct product = buildProduct(OWNER_A, "Leche entera", 1L, true);
    persist(product);

    MvcResult result =
        mockMvc
            .perform(get("/user-products/" + product.getId()))
            .andExpect(status().isOk())
            .andReturn();

    UserProductResponseDto dto =
        objectMapper.readValue(
            result.getResponse().getContentAsByteArray(), UserProductResponseDto.class);

    assertThat(dto.id()).isEqualTo(product.getId());
    assertThat(dto.ownerId()).isEqualTo(OWNER_A);
    assertThat(dto.name()).isEqualTo("Leche entera");
    assertThat(dto.defaultUnit()).isEqualTo(UnitEnum.UNIT);
    assertThat(dto.calories()).isEqualTo(150);
    assertThat(dto.caloriesPer()).isEqualTo(CaloriesPerEnum.G);
    assertThat(dto.shareWithListMembers()).isFalse();
  }

  /** Devuelve 404 cuando el identificador de producto no existe. */
  @Test
  void getById_missing_returns404() throws Exception {
    mockMvc.perform(get("/user-products/99999")).andExpect(status().isNotFound());
  }

  /** Devuelve 404 cuando el producto existe pero está inactivo. */
  @Test
  void getById_inactive_returns404() throws Exception {
    UserProduct product = buildProduct(OWNER_A, "Queso curado", 1L, false);
    persist(product);

    mockMvc.perform(get("/user-products/" + product.getId())).andExpect(status().isNotFound());
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
   * Construye un producto de usuario con los campos mínimos para los tests.
   *
   * @param ownerId propietario del producto
   * @param name nombre del producto
   * @param categoryId categoría del producto
   * @param active si el producto está activo
   * @return entidad {@link UserProduct} con los valores indicados
   */
  private UserProduct buildProduct(UUID ownerId, String name, Long categoryId, boolean active) {
    UserProduct product = new UserProduct();
    product.setOwnerId(ownerId);
    product.setName(name);
    product.setDescription("Descripción de prueba");
    product.setCategoryId(categoryId);
    product.setDefaultUnit(UnitEnum.UNIT);
    product.setCalories(150);
    product.setCaloriesPer(CaloriesPerEnum.G);
    product.setShareWithListMembers(false);
    product.setShareWithFriends(false);
    product.setIsActive(active);
    return product;
  }
}
