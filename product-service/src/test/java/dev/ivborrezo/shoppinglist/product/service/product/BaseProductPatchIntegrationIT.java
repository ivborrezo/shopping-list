package dev.ivborrezo.shoppinglist.product.service.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
 * Test de integración del endpoint {@code PATCH /base-products/{id}}.
 *
 * <p>Verifica la edición parcial de productos base: cambio de código, reemplazo de traducciones,
 * cambio de unidad por defecto, código duplicado y producto inexistente.
 */
@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@AutoConfigureMockMvc
@AutoConfigureTestEntityManager
@TestConstructor(autowireMode = AutowireMode.ALL)
@Transactional
@Testcontainers
class BaseProductPatchIntegrationIT {

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

  private final MockMvc mockMvc;

  private final ObjectMapper objectMapper;

  private final TestEntityManager entityManager;

  BaseProductPatchIntegrationIT(
      MockMvc mockMvc, ObjectMapper objectMapper, TestEntityManager entityManager) {
    this.mockMvc = mockMvc;
    this.objectMapper = objectMapper;
    this.entityManager = entityManager;
  }

  /** Cambia solo el código de un producto y devuelve el DTO actualizado con nombre localizado. */
  @Test
  void patchBaseProduct_updateCode_returns200WithNewCode() throws Exception {
    String body =
        """
        {
          "code": "aged_cheese"
        }
        """;

    MvcResult result =
        mockMvc
            .perform(
                patch("/base-products/3")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Accept-Language", "es")
                    .content(body))
            .andExpect(status().isOk())
            .andReturn();

    BaseProductResponse updated =
        objectMapper.readValue(
            result.getResponse().getContentAsByteArray(), BaseProductResponse.class);

    assertThat(updated.id()).isEqualTo(3L);
    assertThat(updated.code()).isEqualTo("aged_cheese");
    assertThat(updated.name()).isEqualTo("Queso curado");
    assertThat(updated.categoryId()).isEqualTo(1L);
    assertThat(updated.defaultUnit()).isEqualTo(UnitEnum.G);
    assertThat(updated.calories()).isEqualTo(350);
  }

  /** Reemplaza el conjunto completo de traducciones y devuelve los nuevos nombres localizados. */
  @Test
  void patchBaseProduct_replaceTranslations_returns200WithNewNames() throws Exception {
    String body =
        """
        {
          "translations": [
            {"locale": "es", "name": "Cambur"},
            {"locale": "en", "name": "Banana fruit"},
            {"locale": "eu", "name": "Banana berria"}
          ]
        }
        """;

    MvcResult result =
        mockMvc
            .perform(
                patch("/base-products/10")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Accept-Language", "eu")
                    .content(body))
            .andExpect(status().isOk())
            .andReturn();

    BaseProductResponse updated =
        objectMapper.readValue(
            result.getResponse().getContentAsByteArray(), BaseProductResponse.class);

    assertThat(updated.id()).isEqualTo(10L);
    assertThat(updated.code()).isEqualTo("banana");
    assertThat(updated.name()).isEqualTo("Banana berria");
  }

  /** Cambia la unidad por defecto de un producto sin modificar el resto de campos. */
  @Test
  void patchBaseProduct_changeDefaultUnit_returns200WithNewUnit() throws Exception {
    String body =
        """
        {
          "defaultUnit": "KG"
        }
        """;

    MvcResult result =
        mockMvc
            .perform(
                patch("/base-products/4")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Accept-Language", "es")
                    .content(body))
            .andExpect(status().isOk())
            .andReturn();

    BaseProductResponse updated =
        objectMapper.readValue(
            result.getResponse().getContentAsByteArray(), BaseProductResponse.class);

    assertThat(updated.id()).isEqualTo(4L);
    assertThat(updated.code()).isEqualTo("butter");
    assertThat(updated.defaultUnit()).isEqualTo(UnitEnum.KG);
  }

  /**
   * Rechaza con 409 la edición cuando el código indicado ya pertenece a otro producto.
   *
   * <p>La respuesta cumple el shape {@code ProblemDetail} del contrato: content-type {@code
   * application/problem+json} y código {@code DUPLICATE_PRODUCT_CODE}.
   */
  @Test
  void patchBaseProduct_withDuplicateCode_returns409() throws Exception {
    String body =
        """
        {
          "code": "whole_milk"
        }
        """;

    MvcResult result =
        mockMvc
            .perform(
                patch("/base-products/3").contentType(MediaType.APPLICATION_JSON).content(body))
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

  /** Devuelve 404 cuando el identificador de producto base no existe. */
  @Test
  void patchBaseProduct_withNonExistentId_returns404() throws Exception {
    String body =
        """
        {
          "code": "test"
        }
        """;

    mockMvc
        .perform(patch("/base-products/9999").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isNotFound());
  }

  /** Deserialización parcial del shape {@code ProblemDetail} para los asserts de los tests. */
  record ProblemDetailResponse(String code, String title, Integer status) {}
}
