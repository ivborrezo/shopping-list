package dev.ivborrezo.shoppinglist.product.service.category;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.ivborrezo.shoppinglist.product.service.category.dto.CategoryResponseDto;
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
@AutoConfigureTestEntityManager
@TestConstructor(autowireMode = AutowireMode.ALL)
@Transactional
@Testcontainers
class CategoryGetByIdIntegrationIT {

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
  CategoryGetByIdIntegrationIT(
      MockMvc mockMvc, ObjectMapper objectMapper, TestEntityManager entityManager) {
    this.mockMvc = mockMvc;
    this.objectMapper = objectMapper;
    this.entityManager = entityManager;
  }

  /** Devuelve la categoría con el nombre localizado al idioma solicitado. */
  @Test
  void getCategoryById_withEuHeader_returnsCategoryWithLocalizedName() throws Exception {
    MvcResult result =
        mockMvc
            .perform(get("/categories/1").header("Accept-Language", "eu"))
            .andExpect(status().isOk())
            .andReturn();

    CategoryResponseDto category =
        objectMapper.readValue(
            result.getResponse().getContentAsByteArray(), CategoryResponseDto.class);

    assertThat(category.id()).isEqualTo(1L);
    assertThat(category.code()).isEqualTo("dairy");
    assertThat(category.name()).isEqualTo("Esnekiak");
    assertThat(category.isActive()).isTrue();
  }

  /** Devuelve 404 cuando el identificador de categoría no existe. */
  @Test
  void getCategoryById_withNonExistentId_returns404() throws Exception {
    mockMvc.perform(get("/categories/9999")).andExpect(status().isNotFound());
  }
}
