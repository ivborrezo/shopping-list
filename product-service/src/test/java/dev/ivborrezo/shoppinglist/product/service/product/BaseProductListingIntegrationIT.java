package dev.ivborrezo.shoppinglist.product.service.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.ivborrezo.shoppinglist.product.service.common.dto.PagedResponse;
import dev.ivborrezo.shoppinglist.product.service.product.dto.BaseProductResponseDto;
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
 * Test de integración del endpoint {@code GET /base-products} con paginación, filtro por categoría,
 * búsqueda textual e internacionalización según la cabecera {@code Accept-Language}.
 *
 * <p>Trabaja sobre los 30 productos del seed V7 (aplicados por Flyway en el arranque del contexto
 * de test), cubriendo la pila completa sin mocks: {@code DispatcherServlet → BaseProductController
 * → BaseProductService → BaseProductRepository → PostgreSQL}.
 */
@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@AutoConfigureMockMvc
@AutoConfigureTestEntityManager
@TestConstructor(autowireMode = AutowireMode.ALL)
@Transactional
@Testcontainers
class BaseProductListingIntegrationIT {

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

  private final MockMvc mockMvc;

  private final ObjectMapper objectMapper;

  private final TestEntityManager entityManager;

  BaseProductListingIntegrationIT(
      MockMvc mockMvc, ObjectMapper objectMapper, TestEntityManager entityManager) {
    this.mockMvc = mockMvc;
    this.objectMapper = objectMapper;
    this.entityManager = entityManager;
  }

  /** Devuelve una página con los metadatos de paginación y los productos del seed. */
  @Test
  void listBaseProducts_returnsFirstPageWithCorrectMetadata() throws Exception {
    PagedResponse<BaseProductResponseDto> page = getBaseProducts("?page=0&size=5");

    assertThat(page.content()).hasSize(5);
    assertThat(page.page()).isEqualTo(0);
    assertThat(page.size()).isEqualTo(5);
    assertThat(page.totalElements()).isEqualTo(30);
  }

  /** Filtra por categoría y devuelve solo los productos de esa categoría. */
  @Test
  void listBaseProducts_filterByCategoryId_returnsOnlyDairyProducts() throws Exception {
    PagedResponse<BaseProductResponseDto> page = getBaseProducts("?categoryId=1");

    assertThat(page.content()).hasSize(4);
    assertThat(page.content())
        .extracting(BaseProductResponseDto::code)
        .containsExactlyInAnyOrder("whole_milk", "yogurt_natural", "cured_cheese", "butter");
  }

  /**
   * Busca por texto en el nombre localizado y encuentra productos cuya traducción contiene el
   * término, independientemente del idioma.
   */
  @Test
  void listBaseProducts_searchByText_findsMatchingProducts() throws Exception {
    PagedResponse<BaseProductResponseDto> page = getBaseProducts("?text=leche");

    assertThat(page.content()).isNotEmpty();
    assertThat(page.content()).extracting(BaseProductResponseDto::code).contains("whole_milk");
  }

  /** Devuelve los nombres en euskera con la cabecera {@code Accept-Language: eu}. */
  @Test
  void listBaseProducts_withEuHeader_returnsNamesInEuskera() throws Exception {
    PagedResponse<BaseProductResponseDto> page = getBaseProducts("", "eu");

    assertThat(page.content())
        .filteredOn(product -> product.code().equals("whole_milk"))
        .singleElement()
        .extracting(BaseProductResponseDto::name)
        .isEqualTo("Esne osoa");
  }

  /** Devuelve los nombres en español con la cabecera {@code Accept-Language: es}. */
  @Test
  void listBaseProducts_withEsHeader_returnsNamesInSpanish() throws Exception {
    PagedResponse<BaseProductResponseDto> page = getBaseProducts("", "es");

    assertThat(page.content())
        .filteredOn(product -> product.code().equals("whole_milk"))
        .singleElement()
        .extracting(BaseProductResponseDto::name)
        .isEqualTo("Leche entera");
  }

  /** Aplica fallback a inglés cuando no se envía la cabecera {@code Accept-Language}. */
  @Test
  void listBaseProducts_withoutAcceptLanguageHeader_fallsBackToEnglish() throws Exception {
    PagedResponse<BaseProductResponseDto> page = getBaseProducts("", null);

    assertThat(page.content())
        .filteredOn(product -> product.code().equals("whole_milk"))
        .singleElement()
        .extracting(BaseProductResponseDto::name)
        .isEqualTo("Whole milk");
  }

  /** Aplica fallback a inglés cuando el idioma solicitado no está soportado. */
  @Test
  void listBaseProducts_withUnsupportedLocale_fallsBackToEnglish() throws Exception {
    PagedResponse<BaseProductResponseDto> page = getBaseProducts("", "fr");

    assertThat(page.content())
        .filteredOn(product -> product.code().equals("whole_milk"))
        .singleElement()
        .extracting(BaseProductResponseDto::name)
        .isEqualTo("Whole milk");
  }

  /**
   * Ejecuta {@code GET /base-products} con los query params indicados y deserializa la página
   * resultante.
   *
   * @param queryString query params sin signo de interrogación inicial
   * @return página de productos base devuelta por el endpoint
   * @throws Exception si la petición MockMvc o la deserialización fallan
   */
  private PagedResponse<BaseProductResponseDto> getBaseProducts(String queryString)
      throws Exception {
    return getBaseProducts(queryString, null);
  }

  private PagedResponse<BaseProductResponseDto> getBaseProducts(
      String queryString, String acceptLanguage) throws Exception {
    var request = get("/base-products" + (queryString.isEmpty() ? "" : "?" + queryString));
    if (acceptLanguage != null) {
      request = request.header("Accept-Language", acceptLanguage);
    }
    MvcResult result = mockMvc.perform(request).andExpect(status().isOk()).andReturn();

    return objectMapper.readValue(
        result.getResponse().getContentAsByteArray(),
        new TypeReference<PagedResponse<BaseProductResponseDto>>() {});
  }
}
