package dev.ivborrezo.shoppinglist.product.service.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.ivborrezo.shoppinglist.product.service.common.ProductType;
import dev.ivborrezo.shoppinglist.product.service.common.dto.PagedResponse;
import dev.ivborrezo.shoppinglist.product.service.product.dto.ProductReference;
import dev.ivborrezo.shoppinglist.product.service.product.entity.UserFavoriteProduct;
import dev.ivborrezo.shoppinglist.product.service.product.entity.UserRecentProduct;
import java.time.Instant;
import java.util.List;
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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Test de integración del listado de favoritos y recientes con filas huérfanas.
 *
 * <p>Persiste directamente una fila de favorito y otra de reciente que referencian un producto
 * inexistente (id interno sin resolver y snapshot de public id nulo) y comprueba que ambos listados
 * devuelven la referencia conservada con {@code productId} y {@code name} nulos, sin romper la
 * respuesta.
 */
@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@AutoConfigureMockMvc
@AutoConfigureTestEntityManager
@TestConstructor(autowireMode = AutowireMode.ALL)
@Transactional
@Testcontainers
class UserFavoriteOrphanIntegrationIT {

  @Container @ServiceConnection
  static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

  private static final UUID OWNER_ID = UUID.fromString("aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee");

  private static final Long UNRESOLVABLE_PRODUCT_ID = 999999L;

  private final MockMvc mockMvc;

  private final ObjectMapper objectMapper;

  private final TestEntityManager entityManager;

  /**
   * Inyecta las dependencias de test por constructor, sin {@code @Autowired} por campo, coherente
   * con la convención del resto del monorepo.
   *
   * @param mockMvc cliente MockMvc contra el DispatcherServlet real
   * @param objectMapper mapper Jackson para deserializar el body de las respuestas HTTP
   * @param entityManager gestor JPA para inserciones ad hoc dentro de la transacción del test
   */
  UserFavoriteOrphanIntegrationIT(
      MockMvc mockMvc, ObjectMapper objectMapper, TestEntityManager entityManager) {
    this.mockMvc = mockMvc;
    this.objectMapper = objectMapper;
    this.entityManager = entityManager;
  }

  /**
   * Lista los favoritos con una fila huérfana que apunta a un producto inexistente y comprueba que
   * la referencia se conserva con identificador y nombre nulos.
   */
  @Test
  void listFavorites_withUnresolvableProduct_returnsNullReferenceWithoutBreaking()
      throws Exception {
    UserFavoriteProduct favorite = new UserFavoriteProduct();
    favorite.setUserId(OWNER_ID);
    favorite.setProductId(UNRESOLVABLE_PRODUCT_ID);
    favorite.setProductType(ProductType.BASE);
    favorite.setProductPublicId(null);
    favorite.setCreatedAt(Instant.now());
    entityManager.persistAndFlush(favorite);

    MvcResult result =
        mockMvc
            .perform(get("/user-products/favorites").param("ownerId", OWNER_ID.toString()))
            .andExpect(status().isOk())
            .andReturn();

    PagedResponse<ProductReference> page =
        objectMapper.readValue(
            result.getResponse().getContentAsByteArray(),
            new TypeReference<PagedResponse<ProductReference>>() {});

    assertThat(page.content()).hasSize(1);
    assertThat(page.content().get(0).productId()).isNull();
    assertThat(page.content().get(0).name()).isNull();
  }

  /**
   * Lista los recientes con una fila huérfana que apunta a un producto inexistente y comprueba que
   * la referencia se conserva con identificador y nombre nulos.
   */
  @Test
  void listRecents_withUnresolvableProduct_returnsNullReferenceWithoutBreaking() throws Exception {
    UserRecentProduct recent = new UserRecentProduct();
    recent.setUserId(OWNER_ID);
    recent.setProductId(UNRESOLVABLE_PRODUCT_ID);
    recent.setProductType(ProductType.BASE);
    recent.setProductPublicId(null);
    recent.setLastUsedAt(Instant.now());
    entityManager.persistAndFlush(recent);

    MvcResult result =
        mockMvc
            .perform(get("/user-products/recents").param("ownerId", OWNER_ID.toString()))
            .andExpect(status().isOk())
            .andReturn();

    List<ProductReference> recents =
        objectMapper.readValue(
            result.getResponse().getContentAsByteArray(),
            new TypeReference<List<ProductReference>>() {});

    assertThat(recents).hasSize(1);
    assertThat(recents.get(0).productId()).isNull();
    assertThat(recents.get(0).name()).isNull();
  }
}
