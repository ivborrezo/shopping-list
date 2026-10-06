package dev.ivborrezo.shoppinglist.product.service.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.ivborrezo.shoppinglist.product.service.category.repository.CategoryRepository;
import dev.ivborrezo.shoppinglist.product.service.common.CaloriesPerEnum;
import dev.ivborrezo.shoppinglist.product.service.common.UnitEnum;
import dev.ivborrezo.shoppinglist.product.service.product.dto.BaseProductResponse;
import dev.ivborrezo.shoppinglist.product.service.product.repository.BaseProductRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
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
import tools.jackson.databind.ObjectMapper;

/**
 * Test de integración del endpoint {@code GET /base-products/{id}}.
 *
 * <p>Verifica la recuperación de un producto base concreto por su identificador, con nombre y
 * descripción localizados según la cabecera {@code Accept-Language}, y el {@code 404} para
 * identificadores inexistentes.
 */
@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@AutoConfigureMockMvc
@TestConstructor(autowireMode = AutowireMode.ALL)
@Transactional
@Testcontainers
class BaseProductGetByIdIntegrationIT {

  @Container @ServiceConnection
  static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

  private final MockMvc mockMvc;

  private final ObjectMapper objectMapper;

  private final BaseProductRepository baseProductRepository;

  private final CategoryRepository categoryRepository;

  BaseProductGetByIdIntegrationIT(
      MockMvc mockMvc,
      ObjectMapper objectMapper,
      BaseProductRepository baseProductRepository,
      CategoryRepository categoryRepository) {
    this.mockMvc = mockMvc;
    this.objectMapper = objectMapper;
    this.baseProductRepository = baseProductRepository;
    this.categoryRepository = categoryRepository;
  }

  /**
   * Devuelve el producto con nombre y descripción localizados al euskera, y con todos los campos
   * estructurales (no-i18n) del seed.
   */
  @Test
  void getBaseProductById_withEuHeader_returnsProductWithLocalizedNameAndDescription()
      throws Exception {
    UUID productId = baseProductPublicId(1L);
    MvcResult result =
        mockMvc
            .perform(get("/base-products/" + productId).header("Accept-Language", "eu"))
            .andExpect(status().isOk())
            .andReturn();

    BaseProductResponse product =
        objectMapper.readValue(
            result.getResponse().getContentAsByteArray(), BaseProductResponse.class);

    assertThat(product.id()).isEqualTo(productId);
    assertThat(product.code()).isEqualTo("whole_milk");
    assertThat(product.name()).isEqualTo("Esne osoa");
    assertThat(product.description()).isEqualTo("Behi-esne osoa, gaingabetu gabea");
    assertThat(product.categoryId()).isEqualTo(categoryPublicId(1L));
    assertThat(product.defaultUnit()).isEqualTo(UnitEnum.L);
    assertThat(product.calories()).isNull();
    assertThat(product.caloriesPer()).isEqualTo(CaloriesPerEnum.ML);
  }

  /**
   * Devuelve el producto con nombre y descripción localizados al español, verificando un producto
   * con calorías definidas.
   */
  @Test
  void getBaseProductById_withEsHeader_returnsLocalizedNameAndStructuralFields() throws Exception {
    UUID productId = baseProductPublicId(3L);
    MvcResult result =
        mockMvc
            .perform(get("/base-products/" + productId).header("Accept-Language", "es"))
            .andExpect(status().isOk())
            .andReturn();

    BaseProductResponse product =
        objectMapper.readValue(
            result.getResponse().getContentAsByteArray(), BaseProductResponse.class);

    assertThat(product.id()).isEqualTo(productId);
    assertThat(product.code()).isEqualTo("cured_cheese");
    assertThat(product.name()).isEqualTo("Queso curado");
    assertThat(product.description())
        .isEqualTo("Queso de leche de oveja con maduración prolongada");
    assertThat(product.categoryId()).isEqualTo(categoryPublicId(1L));
    assertThat(product.defaultUnit()).isEqualTo(UnitEnum.G);
    assertThat(product.calories()).isEqualTo(350);
    assertThat(product.caloriesPer()).isEqualTo(CaloriesPerEnum.G);
  }

  /** Devuelve 404 cuando el identificador de producto base no existe. */
  @Test
  void getBaseProductById_withNonExistentId_returns404() throws Exception {
    mockMvc
        .perform(get("/base-products/" + UUID.randomUUID()).header("Accept-Language", "es"))
        .andExpect(status().isNotFound());
  }

  /**
   * Resuelve el identificador público del producto base con el identificador interno indicado.
   *
   * @param id identificador interno del producto base del seed
   * @return identificador público del producto base
   */
  private UUID baseProductPublicId(Long id) {
    return baseProductRepository.findById(id).orElseThrow().getPublicId();
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
}
