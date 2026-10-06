package dev.ivborrezo.shoppinglist.product.service.category;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.ivborrezo.shoppinglist.product.service.category.dto.CategoryResponse;
import dev.ivborrezo.shoppinglist.product.service.category.repository.CategoryRepository;
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
 * Test de integración del endpoint {@code GET /categories/{id}}.
 *
 * <p>Verifica la recuperación de una categoría concreta por su identificador, con el nombre
 * localizado según la cabecera {@code Accept-Language}, y el {@code 404} para identificadores
 * inexistentes.
 */
@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@AutoConfigureMockMvc
@TestConstructor(autowireMode = AutowireMode.ALL)
@Transactional
@Testcontainers
class CategoryGetByIdIntegrationIT {

  @Container @ServiceConnection
  static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

  private final MockMvc mockMvc;

  private final ObjectMapper objectMapper;

  private final CategoryRepository categoryRepository;

  /**
   * Inyecta las dependencias de test por constructor, sin {@code @Autowired} por campo, coherente
   * con la convención del resto del monorepo.
   *
   * @param mockMvc cliente MockMvc contra el DispatcherServlet real
   * @param objectMapper mapper Jackson para deserializar el body de las respuestas HTTP
   * @param categoryRepository repositorio de categorías para resolver el identificador público del
   *     seed
   */
  CategoryGetByIdIntegrationIT(
      MockMvc mockMvc, ObjectMapper objectMapper, CategoryRepository categoryRepository) {
    this.mockMvc = mockMvc;
    this.objectMapper = objectMapper;
    this.categoryRepository = categoryRepository;
  }

  /** Devuelve la categoría con el nombre localizado al idioma solicitado. */
  @Test
  void getCategoryById_withEuHeader_returnsCategoryWithLocalizedName() throws Exception {
    UUID categoryId = categoryRepository.findById(1L).orElseThrow().getPublicId();

    MvcResult result =
        mockMvc
            .perform(get("/categories/" + categoryId).header("Accept-Language", "eu"))
            .andExpect(status().isOk())
            .andReturn();

    CategoryResponse category =
        objectMapper.readValue(
            result.getResponse().getContentAsByteArray(), CategoryResponse.class);

    assertThat(category.id()).isEqualTo(categoryId);
    assertThat(category.code()).isEqualTo("dairy");
    assertThat(category.name()).isEqualTo("Esnekiak");
    assertThat(category.isActive()).isTrue();
  }

  /** Devuelve 404 cuando el identificador de categoría no existe. */
  @Test
  void getCategoryById_withNonExistentId_returns404() throws Exception {
    mockMvc.perform(get("/categories/" + UUID.randomUUID())).andExpect(status().isNotFound());
  }
}
