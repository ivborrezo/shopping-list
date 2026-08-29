package dev.ivborrezo.shoppinglist.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.ivborrezo.shoppinglist.product.service.common.CaloriesPerEnum;
import dev.ivborrezo.shoppinglist.product.service.common.UnitEnum;
import dev.ivborrezo.shoppinglist.product.service.product.entity.UserProduct;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.jpa.test.autoconfigure.AutoConfigureTestEntityManager;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.TestConstructor.AutowireMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;

/**
 * Test de integración del contrato de error definido en {@code api-contract.yaml}.
 *
 * <p>Verifica el shape {@code ProblemDetail} (RFC 9457) con content-type {@code
 * application/problem+json} y la extensión {@code code} para cada familia de error del catálogo:
 * recurso inexistente (404), categoría inválida (400), propietario no coincidente (403), código
 * duplicado (409), validación Bean Validation con errores por campo (400) y fallback de excepción
 * no controlada (500).
 */
@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@AutoConfigureMockMvc
@AutoConfigureTestEntityManager
@TestConstructor(autowireMode = AutowireMode.ALL)
@Transactional
@Testcontainers
class ErrorHandlingIntegrationIT {

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

  private static final UUID OWNER_ID = UUID.fromString("aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee");

  private static final UUID OTHER_OWNER_ID =
      UUID.fromString("aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeef");

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
  ErrorHandlingIntegrationIT(
      MockMvc mockMvc, ObjectMapper objectMapper, TestEntityManager entityManager) {
    this.mockMvc = mockMvc;
    this.objectMapper = objectMapper;
    this.entityManager = entityManager;
  }

  /** Devuelve 404 con ProblemDetail cuando la categoría solicitada no existe. */
  @Test
  void getCategory_withNonExistentId_returnsProblemDetailWithCode() throws Exception {
    MvcResult result =
        mockMvc
            .perform(get("/categories/999999"))
            .andExpect(status().isNotFound())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andReturn();

    ProblemDetailResponse response =
        objectMapper.readValue(
            result.getResponse().getContentAsByteArray(), ProblemDetailResponse.class);

    assertThat(response.code()).isEqualTo("CATEGORY_NOT_FOUND");
    assertThat(response.title()).isEqualTo("Category not found");
    assertThat(response.status()).isEqualTo(404);
  }

  /** Rechaza con 400 y ProblemDetail la creación de un producto base con categoría inexistente. */
  @Test
  void createBaseProduct_withInvalidCategory_returnsProblemDetailWithCode() throws Exception {
    String body =
        """
        {
          "code": "invalid_category_probe",
          "categoryId": 999999,
          "defaultUnit": "UNIT",
          "caloriesPer": "G",
          "isActive": true,
          "translations": [{"locale": "es", "name": "Producto de prueba"}]
        }
        """;

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

  /** Rechaza con 403 y ProblemDetail la edición de un producto de usuario de otro propietario. */
  @Test
  void patchUserProduct_withMismatchedOwner_returnsProblemDetailWithCode() throws Exception {
    UserProduct product = buildProduct(OWNER_ID);
    persist(product);

    String body =
        """
        {
          "ownerId": "%s",
          "name": "Intruso"
        }
        """
            .formatted(OTHER_OWNER_ID);

    MvcResult result =
        mockMvc
            .perform(
                patch("/user-products/" + product.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isForbidden())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andReturn();

    ProblemDetailResponse response =
        objectMapper.readValue(
            result.getResponse().getContentAsByteArray(), ProblemDetailResponse.class);

    assertThat(response.code()).isEqualTo("OWNER_MISMATCH");
    assertThat(response.title()).isEqualTo("Owner mismatch");
    assertThat(response.status()).isEqualTo(403);
  }

  /** Rechaza con 409 y ProblemDetail la creación de un producto base con código duplicado. */
  @Test
  void createBaseProduct_withDuplicateCode_returnsProblemDetailWithCode() throws Exception {
    String body =
        """
        {
          "code": "whole_milk",
          "categoryId": 1,
          "defaultUnit": "L",
          "caloriesPer": "ML",
          "isActive": true,
          "translations": [{"locale": "es", "name": "Leche entera"}]
        }
        """;

    MvcResult result =
        mockMvc
            .perform(post("/base-products").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isConflict())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andReturn();

    ProblemDetailResponse response =
        objectMapper.readValue(
            result.getResponse().getContentAsByteArray(), ProblemDetailResponse.class);

    assertThat(response.code()).isEqualTo("DUPLICATE_PRODUCT_CODE");
    assertThat(response.title()).isEqualTo("Duplicate product code");
    assertThat(response.status()).isEqualTo(409);
  }

  /**
   * Rechaza con 400 y ProblemDetail la creación con un campo inválido, incluyendo el error por
   * campo de la validación Bean Validation.
   */
  @Test
  void createBaseProduct_withEmptyCode_returnsValidationProblemDetail() throws Exception {
    String body =
        """
        {
          "code": "",
          "categoryId": 1,
          "defaultUnit": "UNIT",
          "caloriesPer": "G",
          "isActive": true,
          "translations": [{"locale": "es", "name": "Producto sin código"}]
        }
        """;

    MvcResult result =
        mockMvc
            .perform(post("/base-products").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andReturn();

    ValidationProblemDetailResponse response =
        objectMapper.readValue(
            result.getResponse().getContentAsByteArray(), ValidationProblemDetailResponse.class);

    assertThat(response.code()).isEqualTo("VALIDATION_FAILED");
    assertThat(response.title()).isEqualTo("Validation failed");
    assertThat(response.status()).isEqualTo(400);
    assertThat(response.errors()).hasSize(1);
    assertThat(response.errors().get(0).code()).isEqualTo("NotBlank");
    assertThat(response.errors().get(0).field()).isEqualTo("code");
    assertThat(response.errors().get(0).message()).isNotBlank();
  }

  /** Devuelve 500 con ProblemDetail ante una excepción no controlada por el contrato. */
  @Test
  void errorProbe_withUnhandledException_returnsInternalErrorProblemDetail() throws Exception {
    MvcResult result =
        mockMvc
            .perform(get("/error-probe"))
            .andExpect(status().isInternalServerError())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andReturn();

    ProblemDetailResponse response =
        objectMapper.readValue(
            result.getResponse().getContentAsByteArray(), ProblemDetailResponse.class);

    assertThat(response.code()).isEqualTo("INTERNAL_ERROR");
    assertThat(response.title()).isEqualTo("Internal Server Error");
    assertThat(response.status()).isEqualTo(500);
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
    product.setName("Producto del propietario");
    product.setDescription("Descripción de prueba");
    product.setCategoryId(1L);
    product.setDefaultUnit(UnitEnum.UNIT);
    product.setCaloriesPer(CaloriesPerEnum.G);
    product.setShareWithListMembers(false);
    product.setShareWithFriends(false);
    product.setIsActive(true);
    return product;
  }

  /**
   * Configuración de test que registra un controller de prueba para disparar una excepción no
   * controlada. Al ser una clase anidada de esta clase, el controller solo se aplica al contexto de
   * este test.
   */
  @TestConfiguration
  static class ErrorProbeConfig {

    /**
     * Controller de prueba que lanza una excepción no controlada para ejercitar el fallback 500.
     */
    @RestController
    static class ErrorProbeController {

      @GetMapping("/error-probe")
      void probe() {
        throw new RuntimeException("error probe");
      }
    }
  }

  /** Deserialización parcial del shape {@code ProblemDetail} para los asserts de los tests. */
  record ProblemDetailResponse(String code, String title, Integer status) {}

  /** Deserialización del shape de validación con el array de errores por campo. */
  record ValidationProblemDetailResponse(
      String code, String title, Integer status, List<FieldErrorResponse> errors) {}

  /** Deserialización de un error de campo de la validación Bean Validation. */
  record FieldErrorResponse(String code, String field, String message) {}
}
