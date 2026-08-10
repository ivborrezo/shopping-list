package dev.ivborrezo.shoppinglist.product.service.category;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.ivborrezo.shoppinglist.product.service.category.dto.CategoryResponseDto;
import dev.ivborrezo.shoppinglist.product.service.common.dto.PagedResponse;
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
 * Test de integración de la localización de {@code GET /categories} según la cabecera {@code
 * Accept-Language}.
 *
 * <p>Verifica la resolución del nombre de cada categoría desde sus traducciones (es/en/eu), con
 * fallback a inglés cuando el idioma solicitado no está soportado o la cabecera es inválida.
 */
@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@AutoConfigureMockMvc
@AutoConfigureTestEntityManager
@TestConstructor(autowireMode = AutowireMode.ALL)
@Transactional
@Testcontainers
class CategoryI18nIntegrationIT {

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

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
  CategoryI18nIntegrationIT(
      MockMvc mockMvc, ObjectMapper objectMapper, TestEntityManager entityManager) {
    this.mockMvc = mockMvc;
    this.objectMapper = objectMapper;
    this.entityManager = entityManager;
  }

  /** Devuelve el nombre en español de la categoría dairy con la cabecera {@code es}. */
  @Test
  void getCategories_withEsHeader_returnsNamesInSpanish() throws Exception {
    PagedResponse<CategoryResponseDto> page = getCategories("es");

    assertThat(page.totalElements()).isEqualTo(10);
    assertThat(page.content())
        .filteredOn(category -> category.code().equals("dairy"))
        .singleElement()
        .extracting(CategoryResponseDto::name)
        .isEqualTo("Lácteos");
  }

  /** Devuelve el nombre en inglés de la categoría dairy con la cabecera {@code en}. */
  @Test
  void getCategories_withEnHeader_returnsNamesInEnglish() throws Exception {
    PagedResponse<CategoryResponseDto> page = getCategories("en");

    assertThat(page.content())
        .filteredOn(category -> category.code().equals("dairy"))
        .singleElement()
        .extracting(CategoryResponseDto::name)
        .isEqualTo("Dairy");
  }

  /** Devuelve el nombre en euskera de la categoría dairy con la cabecera {@code eu}. */
  @Test
  void getCategories_withEuHeader_returnsNamesInEuskera() throws Exception {
    PagedResponse<CategoryResponseDto> page = getCategories("eu");

    assertThat(page.content())
        .filteredOn(category -> category.code().equals("dairy"))
        .singleElement()
        .extracting(CategoryResponseDto::name)
        .isEqualTo("Esnekiak");
  }

  /** Aplica fallback a inglés cuando la petición no incluye la cabecera {@code Accept-Language}. */
  @Test
  void getCategories_withoutAcceptLanguageHeader_fallsBackToEnglish() throws Exception {
    PagedResponse<CategoryResponseDto> page = getCategories(null);

    assertThat(page.content())
        .filteredOn(category -> category.code().equals("dairy"))
        .singleElement()
        .extracting(CategoryResponseDto::name)
        .isEqualTo("Dairy");
  }

  /** Aplica fallback a inglés cuando el idioma solicitado no está soportado. */
  @Test
  void getCategories_withUnsupportedLocale_fallsBackToEnglish() throws Exception {
    PagedResponse<CategoryResponseDto> page = getCategories("fr");

    assertThat(page.content())
        .filteredOn(category -> category.code().equals("dairy"))
        .singleElement()
        .extracting(CategoryResponseDto::name)
        .isEqualTo("Dairy");
  }

  /** Aplica fallback a inglés cuando la cabecera {@code Accept-Language} es inválida. */
  @Test
  void getCategories_withMalformedHeader_fallsBackToEnglish() throws Exception {
    PagedResponse<CategoryResponseDto> page = getCategories("???");

    assertThat(page.content())
        .filteredOn(category -> category.code().equals("dairy"))
        .singleElement()
        .extracting(CategoryResponseDto::name)
        .isEqualTo("Dairy");
  }

  /**
   * Ejecuta {@code GET /categories} con la cabecera {@code Accept-Language} indicada y deserializa
   * la página de categorías resultante.
   *
   * @param acceptLanguage valor de la cabecera; {@code null} para omitirla
   * @return página de categorías activas devuelta por el endpoint
   * @throws Exception si la petición MockMvc o la deserialización fallan
   */
  private PagedResponse<CategoryResponseDto> getCategories(String acceptLanguage) throws Exception {
    var request = get("/categories");
    if (acceptLanguage != null) {
      request = request.header("Accept-Language", acceptLanguage);
    }
    MvcResult result = mockMvc.perform(request).andExpect(status().isOk()).andReturn();

    return objectMapper.readValue(
        result.getResponse().getContentAsByteArray(),
        new TypeReference<PagedResponse<CategoryResponseDto>>() {});
  }
}
