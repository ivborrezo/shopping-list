package dev.ivborrezo.shoppinglist.product.service.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.ivborrezo.shoppinglist.product.service.common.CaloriesPerEnum;
import dev.ivborrezo.shoppinglist.product.service.common.UnitEnum;
import dev.ivborrezo.shoppinglist.product.service.product.dto.BaseProductResponse;
import org.junit.jupiter.api.Disabled;
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
 * nombre en la respuesta, validación de campos obligatorios vía Bean Validation, y los códigos de
 * error de negocio (código duplicado, locale no soportado) pendientes de {@code @ControllerAdvice}
 * en Rama 7.
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
   * Sin manejo global de errores, un locale no soportado se traduce en 500.
   *
   * <p>Deshabilitado hasta Rama 7: MockMvc en modo MOCK no tiene el filtro de errores del
   * contenedor Servlet, por lo que las excepciones no manejadas se propagan como error de test.
   */
  @Disabled(
      "Requiere @ControllerAdvice (Rama 7): MockMvc en modo MOCK no despacha errores del contenedor")
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

    mockMvc
        .perform(post("/base-products").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest());
  }

  /**
   * Sin manejo global de errores, un código duplicado se traduce en 500.
   *
   * <p>Deshabilitado hasta Rama 7: MockMvc en modo MOCK no tiene el filtro de errores del
   * contenedor Servlet. En Rama 7 el status migrará a 409 según {@code api-contract.yaml}.
   */
  @Disabled(
      "Requiere @ControllerAdvice (Rama 7): MockMvc en modo MOCK no despacha errores del contenedor")
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

    mockMvc
        .perform(post("/base-products").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isConflict());
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
}
