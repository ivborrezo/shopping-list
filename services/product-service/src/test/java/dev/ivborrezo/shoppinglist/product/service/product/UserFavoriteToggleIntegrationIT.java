package dev.ivborrezo.shoppinglist.product.service.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.ivborrezo.shoppinglist.product.service.common.CaloriesPerEnum;
import dev.ivborrezo.shoppinglist.product.service.common.UnitEnum;
import dev.ivborrezo.shoppinglist.product.service.product.dto.FavoriteToggleResponse;
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
 * Test de integración del toggle de favorito sobre el endpoint {@code POST
 * /user-products/{id}/favorite}.
 *
 * <p>Verifica el marcado y desmarcado de un producto como favorito, tanto para productos base como
 * para productos de usuario, así como el rechazo de un {@code productType} inválido y de un
 * producto inexistente.
 */
@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@AutoConfigureMockMvc
@AutoConfigureTestEntityManager
@TestConstructor(autowireMode = AutowireMode.ALL)
@Transactional
@Testcontainers
class UserFavoriteToggleIntegrationIT {

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

  private static final UUID OWNER_ID = UUID.fromString("aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee");

  private final MockMvc mockMvc;

  private final ObjectMapper objectMapper;

  private final TestEntityManager entityManager;

  UserFavoriteToggleIntegrationIT(
      MockMvc mockMvc, ObjectMapper objectMapper, TestEntityManager entityManager) {
    this.mockMvc = mockMvc;
    this.objectMapper = objectMapper;
    this.entityManager = entityManager;
  }

  /**
   * Marca como favorito un producto base no marcado previamente y devuelve {@code favorited=true}.
   */
  @Test
  void toggleFavorite_whenNotAlreadyMarked_returnsFavoritedTrue() throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/user-products/{id}/favorite", 1L)
                    .param("ownerId", OWNER_ID.toString())
                    .param("productType", "BASE"))
            .andExpect(status().isOk())
            .andReturn();

    FavoriteToggleResponse response =
        objectMapper.readValue(
            result.getResponse().getContentAsByteArray(), FavoriteToggleResponse.class);

    assertThat(response.favorited()).isTrue();
  }

  /**
   * Desmarca en la segunda llamada un producto base ya favorito y devuelve {@code favorited=false}.
   */
  @Test
  void toggleFavorite_whenAlreadyMarked_returnsFavoritedFalse() throws Exception {
    mockMvc
        .perform(
            post("/user-products/{id}/favorite", 1L)
                .param("ownerId", OWNER_ID.toString())
                .param("productType", "BASE"))
        .andExpect(status().isOk());

    MvcResult result =
        mockMvc
            .perform(
                post("/user-products/{id}/favorite", 1L)
                    .param("ownerId", OWNER_ID.toString())
                    .param("productType", "BASE"))
            .andExpect(status().isOk())
            .andReturn();

    FavoriteToggleResponse response =
        objectMapper.readValue(
            result.getResponse().getContentAsByteArray(), FavoriteToggleResponse.class);

    assertThat(response.favorited()).isFalse();
  }

  /**
   * Marca y desmarca como favorito un producto de usuario recién creado en llamadas consecutivas.
   */
  @Test
  void toggleFavorite_userProduct_marksAndUnmarks() throws Exception {
    UserProduct product = buildUserProduct();
    entityManager.persist(product);
    entityManager.flush();

    MvcResult first =
        mockMvc
            .perform(
                post("/user-products/{id}/favorite", product.getId())
                    .param("ownerId", OWNER_ID.toString())
                    .param("productType", "USER"))
            .andExpect(status().isOk())
            .andReturn();

    FavoriteToggleResponse firstResponse =
        objectMapper.readValue(
            first.getResponse().getContentAsByteArray(), FavoriteToggleResponse.class);
    assertThat(firstResponse.favorited()).isTrue();

    MvcResult second =
        mockMvc
            .perform(
                post("/user-products/{id}/favorite", product.getId())
                    .param("ownerId", OWNER_ID.toString())
                    .param("productType", "USER"))
            .andExpect(status().isOk())
            .andReturn();

    FavoriteToggleResponse secondResponse =
        objectMapper.readValue(
            second.getResponse().getContentAsByteArray(), FavoriteToggleResponse.class);
    assertThat(secondResponse.favorited()).isFalse();
  }

  /** Rechaza con {@code 404} el toggle de un producto base inexistente. */
  @Test
  void toggleFavorite_nonexistentProduct_returns404() throws Exception {
    mockMvc
        .perform(
            post("/user-products/{id}/favorite", 999999L)
                .param("ownerId", OWNER_ID.toString())
                .param("productType", "BASE"))
        .andExpect(status().isNotFound());
  }

  /**
   * Rechaza con {@code 400} un {@code productType} que no corresponde a ningún tipo de producto.
   */
  @Test
  void toggleFavorite_invalidProductType_returns400() throws Exception {
    mockMvc
        .perform(
            post("/user-products/{id}/favorite", 1L)
                .param("ownerId", OWNER_ID.toString())
                .param("productType", "CATALOG"))
        .andExpect(status().isBadRequest());
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
