package dev.ivborrezo.shoppinglist.product.service.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.ivborrezo.shoppinglist.product.service.category.repository.CategoryRepository;
import dev.ivborrezo.shoppinglist.product.service.common.CaloriesPerEnum;
import dev.ivborrezo.shoppinglist.product.service.common.UnitEnum;
import dev.ivborrezo.shoppinglist.product.service.product.dto.BaseProductResponse;
import dev.ivborrezo.shoppinglist.product.service.product.entity.BaseProduct;
import dev.ivborrezo.shoppinglist.product.service.product.entity.UserProduct;
import dev.ivborrezo.shoppinglist.product.service.product.repository.BaseProductRepository;
import java.util.List;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

/**
 * Test de integración del identificador público de los productos base y de usuario.
 *
 * <p>Verifica que el seed de productos base tiene identificadores públicos únicos y no nulos, que
 * la creación de un producto base resuelve el UUID de categoría de la frontera al id interno Long,
 * que una categoría desconocida se rechaza con {@code INVALID_CATEGORY} y que los productos de
 * usuario reciben un UUID v7 en {@code @PrePersist}.
 */
@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@AutoConfigureMockMvc
@AutoConfigureTestEntityManager
@TestConstructor(autowireMode = AutowireMode.ALL)
@Transactional
@Testcontainers
class BaseProductPublicIdIntegrationIT {

  @Container @ServiceConnection
  static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

  private static final UUID OWNER_ID = UUID.fromString("aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee");

  private final MockMvc mockMvc;

  private final ObjectMapper objectMapper;

  private final TestEntityManager entityManager;

  private final CategoryRepository categoryRepository;

  private final BaseProductRepository baseProductRepository;

  /**
   * Inyecta las dependencias de test por constructor, sin {@code @Autowired} por campo, coherente
   * con la convención del resto del monorepo.
   *
   * @param mockMvc cliente MockMvc contra el DispatcherServlet real
   * @param objectMapper mapper Jackson para deserializar el body de las respuestas HTTP
   * @param entityManager gestor JPA para inserciones ad hoc dentro de la transacción del test
   * @param categoryRepository repositorio de categorías para resolver el identificador público del
   *     seed
   * @param baseProductRepository repositorio de productos base para inspeccionar el seed y recargar
   *     entidades persistidas
   */
  BaseProductPublicIdIntegrationIT(
      MockMvc mockMvc,
      ObjectMapper objectMapper,
      TestEntityManager entityManager,
      CategoryRepository categoryRepository,
      BaseProductRepository baseProductRepository) {
    this.mockMvc = mockMvc;
    this.objectMapper = objectMapper;
    this.entityManager = entityManager;
    this.categoryRepository = categoryRepository;
    this.baseProductRepository = baseProductRepository;
  }

  /** Comprueba que todos los productos base del seed tienen public id no nulo y único. */
  @Test
  void seed_baseProducts_haveUniqueNonNullPublicId() {
    List<BaseProduct> all = baseProductRepository.findAll();

    assertThat(all).isNotEmpty();
    assertThat(all).extracting(BaseProduct::getPublicId).doesNotContainNull();
    assertThat(all).extracting(BaseProduct::getPublicId).doesNotHaveDuplicates();
  }

  /**
   * Crea un producto base indicando la categoría por su UUID y comprueba que la respuesta devuelve
   * ese UUID mientras la entidad persistida guarda el id interno Long correspondiente.
   */
  @Test
  void createBaseProduct_resolvesCategoryUuidToInternalLong() throws Exception {
    UUID dairyCategoryPublicId = categoryPublicId(1L);
    String body =
        """
        {
          "code": "public_id_feature_product",
          "categoryId": "%s",
          "defaultUnit": "UNIT",
          "caloriesPer": "G",
          "isActive": true,
          "translations": [
            {"locale": "es", "name": "Producto de la feature de identificadores públicos"}
          ]
        }
        """
            .formatted(dairyCategoryPublicId);

    MvcResult result =
        mockMvc
            .perform(post("/base-products").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated())
            .andReturn();

    BaseProductResponse created =
        objectMapper.readValue(
            result.getResponse().getContentAsByteArray(), BaseProductResponse.class);

    assertThat(created.categoryId()).isEqualTo(dairyCategoryPublicId);

    BaseProduct persisted = baseProductRepository.findByPublicId(created.id()).orElseThrow();
    Long expectedInternalId =
        categoryRepository.findByPublicId(dairyCategoryPublicId).orElseThrow().getId();
    assertThat(persisted.getCategoryId()).isEqualTo(expectedInternalId);
  }

  /**
   * Rechaza con 400 la creación de un producto base cuya categoría no existe.
   *
   * <p>La respuesta cumple el shape {@code ProblemDetail} del contrato: content-type {@code
   * application/problem+json} y código {@code INVALID_CATEGORY}.
   */
  @Test
  void createBaseProduct_withUnknownCategory_returnsInvalidCategory() throws Exception {
    String body =
        """
        {
          "code": "public_id_feature_unknown_category",
          "categoryId": "%s",
          "defaultUnit": "UNIT",
          "caloriesPer": "G",
          "isActive": true,
          "translations": [
            {"locale": "es", "name": "Producto con categoría desconocida"}
          ]
        }
        """
            .formatted(UUID.randomUUID());

    MvcResult result =
        mockMvc
            .perform(post("/base-products").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andReturn();

    ProblemDetailResponse response =
        objectMapper.readValue(
            result.getResponse().getContentAsByteArray(), ProblemDetailResponse.class);

    assertThat(response.code()).isEqualTo("INVALID_CATEGORY");
    assertThat(response.title()).isEqualTo("Invalid category");
    assertThat(response.status()).isEqualTo(400);
  }

  /**
   * Persiste un producto de usuario nuevo y comprueba que recibe un identificador público UUID v7.
   */
  @Test
  void persist_userProduct_assignsVersion7PublicId() {
    UserProduct product = buildUserProduct();

    entityManager.persistAndFlush(product);

    assertThat(product.getPublicId()).isNotNull();
    assertThat(product.getPublicId().version()).isEqualTo(7);
    assertThat(product.getId()).isNotNull();
  }

  /**
   * Resuelve el identificador público de la categoría con el identificador interno indicado.
   *
   * @param id identificador interno de la categoría del seed
   * @return identificador público de la categoría
   */
  private UUID categoryPublicId(Long id) {
    return categoryRepository.findById(id).orElseThrow().getPublicId();
  }

  /**
   * Construye un producto de usuario activo con los campos mínimos para los tests.
   *
   * @return entidad {@link UserProduct} con los valores indicados
   */
  private UserProduct buildUserProduct() {
    UserProduct product = new UserProduct();
    product.setOwnerId(OWNER_ID);
    product.setName("Producto de la feature de public id");
    product.setDefaultUnit(UnitEnum.UNIT);
    product.setCaloriesPer(CaloriesPerEnum.G);
    product.setShareWithListMembers(false);
    product.setShareWithFriends(false);
    product.setIsActive(true);
    return product;
  }

  /** Deserialización parcial del shape {@code ProblemDetail} para los asserts de los tests. */
  record ProblemDetailResponse(String code, String title, Integer status) {}
}
