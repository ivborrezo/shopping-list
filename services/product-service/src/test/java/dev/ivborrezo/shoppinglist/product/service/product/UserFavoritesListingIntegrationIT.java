package dev.ivborrezo.shoppinglist.product.service.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.ivborrezo.shoppinglist.product.service.common.CaloriesPerEnum;
import dev.ivborrezo.shoppinglist.product.service.common.ProductType;
import dev.ivborrezo.shoppinglist.product.service.common.UnitEnum;
import dev.ivborrezo.shoppinglist.product.service.common.dto.PagedResponse;
import dev.ivborrezo.shoppinglist.product.service.product.dto.ProductReference;
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
 * Test de integración del listado de favoritos sobre el endpoint {@code GET
 * /user-products/favorites}.
 *
 * <p>Marca productos base y de usuario como favoritos vía el endpoint de toggle y verifica que el
 * listado devuelve los nombres resueltos según el tipo de producto, la paginación y el orden de más
 * reciente a más antiguo.
 */
@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@AutoConfigureMockMvc
@AutoConfigureTestEntityManager
@TestConstructor(autowireMode = AutowireMode.ALL)
@Transactional
@Testcontainers
class UserFavoritesListingIntegrationIT {

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

  private static final UUID OWNER_ID = UUID.fromString("aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee");

  private final MockMvc mockMvc;

  private final ObjectMapper objectMapper;

  private final TestEntityManager entityManager;

  UserFavoritesListingIntegrationIT(
      MockMvc mockMvc, ObjectMapper objectMapper, TestEntityManager entityManager) {
    this.mockMvc = mockMvc;
    this.objectMapper = objectMapper;
    this.entityManager = entityManager;
  }

  /**
   * Lista los favoritos de un usuario con productos base y de usuario, resolviendo el nombre de
   * cada uno según su tipo y en el idioma de la petición.
   */
  @Test
  void listFavorites_withBaseAndUserProducts_returnsPagedWithResolvedNames() throws Exception {
    markFavorite(1L, "BASE", "es");

    UserProduct product = buildUserProduct();
    entityManager.persist(product);
    entityManager.flush();
    markFavorite(product.getId(), "USER", null);

    PagedResponse<ProductReference> page = getFavorites("ownerId=" + OWNER_ID, "es");

    assertThat(page.content()).hasSize(2);
    assertThat(page.totalElements()).isEqualTo(2);
    assertThat(page.content())
        .extracting(ProductReference::name)
        .containsExactlyInAnyOrder("Leche entera", "Mi producto");
    assertThat(page.content())
        .extracting(ProductReference::productType)
        .containsExactlyInAnyOrder(ProductType.BASE, ProductType.USER);
  }

  /**
   * Devuelve los favoritos paginados en orden de más reciente a más antiguo según {@code
   * createdAt}.
   */
  @Test
  void listFavorites_paginated_returnsPageMetadataAndCreatedAtDescOrder() throws Exception {
    markFavorite(1L, "BASE", null);
    markFavorite(2L, "BASE", null);
    markFavorite(3L, "BASE", null);

    PagedResponse<ProductReference> page = getFavorites("ownerId=" + OWNER_ID + "&size=2", null);

    assertThat(page.content()).hasSize(2);
    assertThat(page.totalElements()).isEqualTo(3);
    assertThat(page.page()).isZero();
    assertThat(page.size()).isEqualTo(2);
    assertThat(page.content()).extracting(ProductReference::productId).containsExactly(3L, 2L);
  }

  /** Devuelve una página vacía cuando el usuario no tiene ningún favorito. */
  @Test
  void listFavorites_empty_returnsEmptyPage() throws Exception {
    PagedResponse<ProductReference> page = getFavorites("ownerId=" + OWNER_ID, null);

    assertThat(page.content()).isEmpty();
    assertThat(page.totalElements()).isZero();
  }

  /**
   * Ejecuta {@code GET /user-products/favorites} con los query params indicados y deserializa la
   * página resultante.
   *
   * @param queryString query params sin signo de interrogación inicial
   * @param acceptLanguage idioma de la petición en el que se resuelven los nombres de los productos
   *     base; {@code null} para omitir la cabecera
   * @return página de referencias a producto devuelta por el endpoint
   * @throws Exception si la petición MockMvc o la deserialización fallan
   */
  private PagedResponse<ProductReference> getFavorites(String queryString, String acceptLanguage)
      throws Exception {
    var request = get("/user-products/favorites?" + queryString);
    if (acceptLanguage != null) {
      request = request.header("Accept-Language", acceptLanguage);
    }
    MvcResult result = mockMvc.perform(request).andExpect(status().isOk()).andReturn();

    return objectMapper.readValue(
        result.getResponse().getContentAsByteArray(),
        new TypeReference<PagedResponse<ProductReference>>() {});
  }

  /**
   * Marca como favorito el producto indicado vía el endpoint de toggle.
   *
   * @param productId identificador del producto a marcar
   * @param productType tipo de producto ({@code BASE} o {@code USER})
   * @param acceptLanguage idioma de la petición; {@code null} para omitir la cabecera
   * @throws Exception si la petición MockMvc falla
   */
  private void markFavorite(Long productId, String productType, String acceptLanguage)
      throws Exception {
    var request =
        post("/user-products/{id}/favorite", productId)
            .param("ownerId", OWNER_ID.toString())
            .param("productType", productType);
    if (acceptLanguage != null) {
      request = request.header("Accept-Language", acceptLanguage);
    }
    mockMvc.perform(request).andExpect(status().isOk());
  }

  /**
   * Construye un producto de usuario activo con los campos mínimos para los tests.
   *
   * @return entidad {@link UserProduct} con los valores indicados
   */
  private UserProduct buildUserProduct() {
    UserProduct product = new UserProduct();
    product.setOwnerId(OWNER_ID);
    product.setName("Mi producto");
    product.setDefaultUnit(UnitEnum.UNIT);
    product.setCaloriesPer(CaloriesPerEnum.G);
    product.setShareWithListMembers(false);
    product.setShareWithFriends(false);
    product.setIsActive(true);
    return product;
  }
}
