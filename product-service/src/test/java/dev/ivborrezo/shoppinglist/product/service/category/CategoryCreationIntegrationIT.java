package dev.ivborrezo.shoppinglist.product.service.category;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.ivborrezo.shoppinglist.product.service.category.dto.CategoryResponse;
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
 * Test de integración del endpoint {@code POST /categories}.
 *
 * <p>Verifica la creación de categorías con sus traducciones (es/en/eu), la localización del nombre
 * en la respuesta, y los códigos de error que produce Spring por defecto sin manejo global de
 * excepciones.
 */
@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@AutoConfigureMockMvc
@AutoConfigureTestEntityManager
@TestConstructor(autowireMode = AutowireMode.ALL)
@Transactional
@Testcontainers
class CategoryCreationIntegrationIT {

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
  CategoryCreationIntegrationIT(
      MockMvc mockMvc, ObjectMapper objectMapper, TestEntityManager entityManager) {
    this.mockMvc = mockMvc;
    this.objectMapper = objectMapper;
    this.entityManager = entityManager;
  }

  /** Crea una categoría con traducciones en los tres idiomas y devuelve el nombre localizado. */
  @Test
  void createCategory_withAllTranslations_returns201AndLocalizedName() throws Exception {
    String body =
        """
        {
          "code": "cleaning",
          "isActive": true,
          "translations": [
            {"locale": "es", "name": "Limpieza"},
            {"locale": "en", "name": "Cleaning"},
            {"locale": "eu", "name": "Garbiketa"}
          ]
        }
        """;

    MvcResult result =
        mockMvc
            .perform(
                post("/categories")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Accept-Language", "eu")
                    .content(body))
            .andExpect(status().isCreated())
            .andExpect(header().exists("Location"))
            .andReturn();

    CategoryResponse created =
        objectMapper.readValue(
            result.getResponse().getContentAsByteArray(), CategoryResponse.class);

    assertThat(created.code()).isEqualTo("cleaning");
    assertThat(created.isActive()).isTrue();
    assertThat(created.name()).isEqualTo("Garbiketa");
    assertThat(result.getResponse().getHeader("Location")).contains("/categories/");
  }

  /** Rechaza la petición sin el campo {@code translations} con 400. */
  @Test
  void createCategory_withoutTranslations_returns400() throws Exception {
    String body =
        """
        {
          "code": "cleaning",
          "isActive": true
        }
        """;

    mockMvc
        .perform(post("/categories").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest());
  }

  /**
   * Sin manejo global de errores, un locale no soportado se traduce en 500.
   *
   * <p>Deshabilitado hasta Rama 6: MockMvc en modo MOCK no tiene el filtro de errores del
   * contenedor Servlet, por lo que las excepciones no manejadas se propagan como error de test en
   * lugar de devolver 500. Se reactivará cuando exista {@code @RestControllerAdvice}.
   */
  @Disabled(
      "Requiere @ControllerAdvice (Rama 6): MockMvc en modo MOCK no despacha errores del contenedor")
  @Test
  void createCategory_withUnsupportedLocale_returns500() throws Exception {
    String body =
        """
        {
          "code": "cleaning",
          "isActive": true,
          "translations": [
            {"locale": "fr", "name": "Nettoyage"}
          ]
        }
        """;

    mockMvc
        .perform(post("/categories").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isInternalServerError());
  }

  /**
   * Sin manejo global de errores, un code duplicado se traduce en 500.
   *
   * <p>Deshabilitado hasta Rama 6: MockMvc en modo MOCK no tiene el filtro de errores del
   * contenedor Servlet, por lo que las excepciones no manejadas se propagan como error de test en
   * lugar de devolver 500. Se reactivará cuando exista {@code @RestControllerAdvice}. En Rama 6 el
   * status esperado migrará a 409 según {@code api-contract.yaml}.
   */
  @Disabled(
      "Requiere @ControllerAdvice (Rama 6): MockMvc en modo MOCK no despacha errores del contenedor")
  @Test
  void createCategory_withDuplicateCode_returns500() throws Exception {
    String body =
        """
        {
          "code": "dairy",
          "isActive": true,
          "translations": [
            {"locale": "es", "name": "Lácteos"}
          ]
        }
        """;

    mockMvc
        .perform(post("/categories").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isInternalServerError());
  }

  /** Rechaza la petición con {@code code} vacío con 400. */
  @Test
  void createCategory_withEmptyCode_returns400() throws Exception {
    String body =
        """
        {
          "code": "",
          "isActive": true,
          "translations": [
            {"locale": "es", "name": "Limpieza"}
          ]
        }
        """;

    mockMvc
        .perform(post("/categories").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest());
  }
}
