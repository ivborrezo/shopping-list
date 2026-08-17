package dev.ivborrezo.shoppinglist.product.service.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.ivborrezo.shoppinglist.product.service.common.CaloriesPerEnum;
import dev.ivborrezo.shoppinglist.product.service.common.UnitEnum;
import dev.ivborrezo.shoppinglist.product.service.product.dto.BaseProductResponse;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;

/**
 * Test de integración del endpoint {@code POST /base-products}.
 *
 * <p>Verifica la creación de productos base con sus traducciones (es/en/eu), la localización del
 * nombre en la respuesta, la validación de campos obligatorios vía Bean Validation y el contrato de
 * error {@code ProblemDetail} del {@code api-contract.yaml} (locale no soportado y código
 * duplicado).
 */
@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@AutoConfigureMockMvc
@AutoConfigureTestEntityManager
@TestConstructor(autowireMode = AutowireMode.ALL)
@Transactional
@Testcontainers
class BaseProductCreationIntegrationIT {

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

  private final MockMvc mockMvc;

  private final ObjectMapper objectMapper;

  private final TestEntityManager entityManager;

  BaseProductCreationIntegrationIT(
      MockMvc mockMvc, ObjectMapper objectMapper, TestEntityManager entityManager) {
    this.mockMvc = mockMvc;
    this.objectMapper = objectMapper;
    this.entityManager = entityManager;
  }

  /**
   * Crea un producto base con traducciones en los tres idiomas, incluyendo descripciones
   * opcionales, y devuelve el nombre localizado al euskera.
   */
  @Test
  void createBaseProduct_withAllTranslations_returns201AndLocalizedName() throws Exception {
    String body =
        """
        {
          "code": "free_range_eggs",
          "categoryId": 1,
          "defaultUnit": "UNIT",
          "calories": 155,
          "caloriesPer": "G",
          "isActive": true,
          "translations": [
            {"locale": "es", "name": "Huevos camperos", "description": "Docena de huevos de gallinas criadas en libertad"},
            {"locale": "en", "name": "Free-range eggs", "description": "Dozen eggs from free-range hens"},
            {"locale": "eu", "name": "Arrautzak kanpoan", "description": "Hamabiko askatasunean hazitako oiloen arrautzak"}
          ]
        }
        """;

    MvcResult result =
        mockMvc
            .perform(
                post("/base-products")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Accept-Language", "eu")
                    .content(body))
            .andExpect(status().isCreated())
            .andExpect(header().exists("Location"))
            .andReturn();

    BaseProductResponse created =
        objectMapper.readValue(
            result.getResponse().getContentAsByteArray(), BaseProductResponse.class);

    assertThat(created.code()).isEqualTo("free_range_eggs");
    assertThat(created.name()).isEqualTo("Arrautzak kanpoan");
    assertThat(created.description()).isEqualTo("Hamabiko askatasunean hazitako oiloen arrautzak");
    assertThat(created.categoryId()).isEqualTo(1L);
    assertThat(created.defaultUnit()).isEqualTo(UnitEnum.UNIT);
    assertThat(created.calories()).isEqualTo(155);
    assertThat(created.caloriesPer()).isEqualTo(CaloriesPerEnum.G);
    assertThat(created.isActive()).isTrue();
    assertThat(result.getResponse().getHeader("Location")).contains("/base-products/");
  }

  /** Rechaza con 400 un {@code defaultUnit} que no corresponde a ninguna {@link UnitEnum}. */
  @Test
  void createBaseProduct_withInvalidDefaultUnit_returns400() throws Exception {
    String body =
        """
        {
          "code": "invalid_unit_product",
          "categoryId": 1,
          "defaultUnit": "XYZ",
          "caloriesPer": "G",
          "isActive": true,
          "translations": [
            {"locale": "es", "name": "Producto con unidad inválida"}
          ]
        }
        """;

    mockMvc
        .perform(post("/base-products").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest());
  }

  /**
   * Rechaza con 400 la creación cuando alguna traducción usa un locale no soportado.
   *
   * <p>La respuesta cumple el shape {@code ProblemDetail} del contrato: content-type {@code
   * application/problem+json} y código {@code UNSUPPORTED_LOCALE}.
   */
  @Test
  void createBaseProduct_withUnsupportedLocale_returns400() throws Exception {
    String body =
        """
        {
          "code": "baguette",
          "categoryId": 2,
          "defaultUnit": "UNIT",
          "caloriesPer": "G",
          "isActive": true,
          "translations": [
            {"locale": "fr", "name": "Baguette"}
          ]
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

    assertThat(response.code()).isEqualTo("UNSUPPORTED_LOCALE");
    assertThat(response.title()).isEqualTo("Unsupported locale");
    assertThat(response.status()).isEqualTo(400);
  }

  /**
   * Rechaza con 409 la creación cuando el código ya existe en el catálogo.
   *
   * <p>La respuesta cumple el shape {@code ProblemDetail} del contrato: content-type {@code
   * application/problem+json} y código {@code DUPLICATE_PRODUCT_CODE}.
   */
  @Test
  void createBaseProduct_withDuplicateCode_returns409() throws Exception {
    String body =
        """
        {
          "code": "whole_milk",
          "categoryId": 1,
          "defaultUnit": "L",
          "caloriesPer": "ML",
          "isActive": true,
          "translations": [
            {"locale": "es", "name": "Leche entera"}
          ]
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

  /** Rechaza la petición con {@code code} vacío con 400 vía Bean Validation. */
  @Test
  void createBaseProduct_withEmptyCode_returns400() throws Exception {
    String body =
        """
        {
          "code": "",
          "categoryId": 1,
          "defaultUnit": "UNIT",
          "caloriesPer": "G",
          "isActive": true,
          "translations": [
            {"locale": "es", "name": "Producto sin código"}
          ]
        }
        """;

    mockMvc
        .perform(post("/base-products").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest());
  }

  /** Rechaza la petición sin el campo {@code translations} con 400 vía Bean Validation. */
  @Test
  void createBaseProduct_withoutTranslations_returns400() throws Exception {
    String body =
        """
        {
          "code": "some_product",
          "categoryId": 1,
          "defaultUnit": "UNIT",
          "caloriesPer": "G",
          "isActive": true
        }
        """;

    mockMvc
        .perform(post("/base-products").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest());
  }

  /** Deserialización parcial del shape {@code ProblemDetail} para los asserts de los tests. */
  record ProblemDetailResponse(String code, String title, Integer status) {}
}
