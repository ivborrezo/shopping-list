package dev.ivborrezo.shoppinglist.product.service.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.ivborrezo.shoppinglist.product.service.common.CaloriesPerEnum;
import dev.ivborrezo.shoppinglist.product.service.common.UnitEnum;
import dev.ivborrezo.shoppinglist.product.service.common.dto.PagedResponse;
import dev.ivborrezo.shoppinglist.product.service.product.dto.UserProductResponse;
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
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Test de integración del endpoint {@code GET /user-products}.
 *
 * <p>Inserta los productos de usuario vía {@link TestEntityManager} dentro de la transacción del
 * test y verifica el listado paginado por propietario, el filtro por categoría y el {@code 400}
 * para un {@code ownerId} mal formado.
 */
@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@AutoConfigureMockMvc
@AutoConfigureTestEntityManager
@TestConstructor(autowireMode = AutowireMode.ALL)
@Transactional
@Testcontainers
class UserProductListingIntegrationIT {

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

  private static final UUID OWNER_A = UUID.fromString("aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeee1");

  private static final UUID OWNER_B = UUID.fromString("aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeee2");

  private final MockMvc mockMvc;

  private final ObjectMapper objectMapper;

  private final TestEntityManager entityManager;

  UserProductListingIntegrationIT(
      MockMvc mockMvc, ObjectMapper objectMapper, TestEntityManager entityManager) {
    this.mockMvc = mockMvc;
    this.objectMapper = objectMapper;
    this.entityManager = entityManager;
  }

  /** Devuelve solo los productos activos del propietario indicado en {@code ownerId}. */
  @Test
  void list_byOwner_returnsOnlyThatOwnersProducts() throws Exception {
    UserProduct milk = buildProduct(OWNER_A, "Leche entera", 1L, true);
    UserProduct cheese = buildProduct(OWNER_A, "Queso curado", 2L, true);
    UserProduct bread = buildProduct(OWNER_B, "Pan de molde", 1L, true);
    persist(milk, cheese, bread);

    PagedResponse<UserProductResponse> page = getUserProducts("ownerId=" + OWNER_A);

    assertThat(page.content()).hasSize(2);
    assertThat(page.page()).isEqualTo(0);
    assertThat(page.totalElements()).isEqualTo(2);
    assertThat(page.content()).extracting(UserProductResponse::ownerId).containsOnly(OWNER_A);
  }

  /** Filtra por categoría y devuelve solo los productos del propietario de esa categoría. */
  @Test
  void list_filteredByCategory_returnsOnlyCategoryProducts() throws Exception {
    UserProduct milk = buildProduct(OWNER_A, "Leche entera", 1L, true);
    UserProduct cheese = buildProduct(OWNER_A, "Queso curado", 2L, true);
    persist(milk, cheese);

    PagedResponse<UserProductResponse> page =
        getUserProducts("ownerId=" + OWNER_A + "&categoryId=1");

    assertThat(page.content()).hasSize(1);
    assertThat(page.content().get(0).categoryId()).isEqualTo(1L);
  }

  /** Devuelve {@code 400} cuando {@code ownerId} no es un UUID válido. */
  @Test
  void list_withMalformedOwnerId_returns400() throws Exception {
    mockMvc.perform(get("/user-products?ownerId=not-a-uuid")).andExpect(status().isBadRequest());
  }

  /**
   * Ejecuta {@code GET /user-products} con los query params indicados y deserializa la página
   * resultante.
   *
   * @param queryString query params sin signo de interrogación inicial
   * @return página de productos de usuario devuelta por el endpoint
   * @throws Exception si la petición MockMvc o la deserialización fallan
   */
  private PagedResponse<UserProductResponse> getUserProducts(String queryString) throws Exception {
    MvcResult result =
        mockMvc
            .perform(get("/user-products?" + queryString))
            .andExpect(status().isOk())
            .andReturn();

    return objectMapper.readValue(
        result.getResponse().getContentAsByteArray(),
        new TypeReference<PagedResponse<UserProductResponse>>() {});
  }

  /**
   * Persiste los productos indicados dentro de la transacción del test y fuerza el {@code flush}
   * para que JPA asigne los identificadores generados.
   *
   * @param products productos de usuario a insertar
   */
  private void persist(UserProduct... products) {
    for (UserProduct product : products) {
      entityManager.persist(product);
    }
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
