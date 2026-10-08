package dev.ivborrezo.shoppinglist.list.service.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

/**
 * Test de integración del contrato de error definido en {@code api-contract.yaml}.
 *
 * <p>Verifica el shape {@code ProblemDetail} (RFC 9457) con content-type {@code
 * application/problem+json} y la extensión {@code code}: errores del catálogo vía {@link
 * BusinessException}, parámetros problemáticos con {@code code=VALIDATION_FAILED} (tipo no
 * convertible, obligatorio ausente, body inválido o no procesable) y fallback de excepción no
 * controlada (500).
 *
 * <p>Al no existir aún los controllers de listas, el test registra controllers de prueba que
 * ejercitan el manejador global.
 */
@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@AutoConfigureMockMvc
@TestConstructor(autowireMode = AutowireMode.ALL)
@Transactional
@Testcontainers
class GlobalExceptionHandlerIntegrationIT {

  @Container @ServiceConnection
  static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

  private final MockMvc mockMvc;

  private final ObjectMapper objectMapper;

  /**
   * Inyecta las dependencias de test por constructor, sin {@code @Autowired} por campo, coherente
   * con la convención del resto del monorepo.
   *
   * @param mockMvc cliente MockMvc contra el DispatcherServlet real
   * @param objectMapper mapper Jackson para deserializar el body de las respuestas HTTP
   */
  GlobalExceptionHandlerIntegrationIT(MockMvc mockMvc, ObjectMapper objectMapper) {
    this.mockMvc = mockMvc;
    this.objectMapper = objectMapper;
  }

  /** Devuelve 404 con ProblemDetail cuando el servicio lanza un error del catálogo. */
  @Test
  void businessProbe_withCatalogueError_returnsProblemDetailWithCode() throws Exception {
    MvcResult result =
        mockMvc
            .perform(get("/probe/business"))
            .andExpect(status().isNotFound())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andReturn();

    ProblemDetailResponse response = readProblem(result);

    assertThat(response.code()).isEqualTo("LIST_NOT_FOUND");
    assertThat(response.title()).isEqualTo("List not found");
    assertThat(response.status()).isEqualTo(404);
  }

  /** Devuelve 400 con {@code VALIDATION_FAILED} cuando falta un parámetro obligatorio. */
  @Test
  void missingRequestParam_returnsValidationProblemDetail() throws Exception {
    MvcResult result =
        mockMvc
            .perform(get("/probe/param"))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andReturn();

    ValidationProblemDetailResponse response = readValidation(result);

    assertThat(response.code()).isEqualTo("VALIDATION_FAILED");
    assertThat(response.title()).isEqualTo("Validation failed");
    assertThat(response.status()).isEqualTo(400);
    assertThat(response.errors()).hasSize(1);
    assertThat(response.errors().get(0).code()).isEqualTo("missing");
    assertThat(response.errors().get(0).field()).isEqualTo("ownerId");
  }

  /** Devuelve 400 con {@code VALIDATION_FAILED} cuando un query param no es convertible. */
  @Test
  void nonUuidRequestParam_returnsValidationProblemDetail() throws Exception {
    MvcResult result =
        mockMvc
            .perform(get("/probe/param").param("ownerId", "not-a-uuid"))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andReturn();

    ValidationProblemDetailResponse response = readValidation(result);

    assertThat(response.code()).isEqualTo("VALIDATION_FAILED");
    assertThat(response.errors()).hasSize(1);
    assertThat(response.errors().get(0).code()).isEqualTo("typeMismatch");
    assertThat(response.errors().get(0).field()).isEqualTo("ownerId");
  }

  /** Devuelve 400 con {@code VALIDATION_FAILED} cuando un path variable no es convertible. */
  @Test
  void nonUuidPathVariable_returnsValidationProblemDetail() throws Exception {
    MvcResult result =
        mockMvc
            .perform(get("/probe/path/not-a-uuid"))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andReturn();

    ValidationProblemDetailResponse response = readValidation(result);

    assertThat(response.code()).isEqualTo("VALIDATION_FAILED");
    assertThat(response.errors()).hasSize(1);
    assertThat(response.errors().get(0).code()).isEqualTo("typeMismatch");
    assertThat(response.errors().get(0).field()).isEqualTo("id");
  }

  /** Devuelve 400 con {@code VALIDATION_FAILED} y errores por campo cuando el body no valida. */
  @Test
  void invalidRequestBody_returnsValidationProblemDetailWithFieldErrors() throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/probe/body")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andReturn();

    ValidationProblemDetailResponse response = readValidation(result);

    assertThat(response.code()).isEqualTo("VALIDATION_FAILED");
    assertThat(response.errors()).hasSize(1);
    assertThat(response.errors().get(0).code()).isEqualTo("NotBlank");
    assertThat(response.errors().get(0).field()).isEqualTo("name");
    assertThat(response.errors().get(0).message()).isNotBlank();
  }

  /** Devuelve 400 con {@code VALIDATION_FAILED} cuando el body JSON no es procesable. */
  @Test
  void malformedRequestBody_returnsValidationProblemDetail() throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/probe/body").contentType(MediaType.APPLICATION_JSON).content("{ not json"))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andReturn();

    ProblemDetailResponse response = readProblem(result);

    assertThat(response.code()).isEqualTo("VALIDATION_FAILED");
    assertThat(response.title()).isEqualTo("Validation failed");
    assertThat(response.status()).isEqualTo(400);
  }

  /** Devuelve 400 con {@code VALIDATION_FAILED} cuando falla una constraint de parámetro. */
  @Test
  void methodValidationFailure_returnsValidationProblemDetail() throws Exception {
    MvcResult result =
        mockMvc
            .perform(get("/probe/method-validation").param("name", " "))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andReturn();

    ValidationProblemDetailResponse response = readValidation(result);

    assertThat(response.code()).isEqualTo("VALIDATION_FAILED");
    assertThat(response.errors()).hasSize(1);
    assertThat(response.errors().get(0).code()).isEqualTo("NotBlank");
    assertThat(response.errors().get(0).field()).isEqualTo("name");
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

    ProblemDetailResponse response = readProblem(result);

    assertThat(response.code()).isEqualTo("INTERNAL_ERROR");
    assertThat(response.title()).isEqualTo("Internal Server Error");
    assertThat(response.status()).isEqualTo(500);
  }

  /**
   * Deserializa el body como shape reducido de {@code ProblemDetail}.
   *
   * @param result resultado de MockMvc con el body a leer
   * @return shape reducido del problema
   * @throws Exception si el body no se puede deserializar
   */
  private ProblemDetailResponse readProblem(MvcResult result) throws Exception {
    return objectMapper.readValue(
        result.getResponse().getContentAsByteArray(), ProblemDetailResponse.class);
  }

  /**
   * Deserializa el body como shape de {@code ProblemDetail} de validación con errores por campo.
   *
   * @param result resultado de MockMvc con el body a leer
   * @return shape de validación con el array de errores
   * @throws Exception si el body no se puede deserializar
   */
  private ValidationProblemDetailResponse readValidation(MvcResult result) throws Exception {
    return objectMapper.readValue(
        result.getResponse().getContentAsByteArray(), ValidationProblemDetailResponse.class);
  }

  /**
   * Configuración de test que registra controllers de prueba para ejercitar cada familia de error.
   * Al ser una clase anidada de esta clase, los controllers solo se aplican al contexto de este
   * test.
   */
  @TestConfiguration
  static class ErrorProbeConfig {

    /** Controller de prueba que lanza un error del catálogo. */
    @RestController
    static class BusinessProbeController {

      @GetMapping("/probe/business")
      void probe() {
        throw new BusinessException(ErrorCode.LIST_NOT_FOUND);
      }
    }

    /** Controller de prueba con un query param obligatorio de tipo UUID. */
    @RestController
    static class ParamProbeController {

      @GetMapping("/probe/param")
      void probe(@RequestParam UUID ownerId) {}
    }

    /** Controller de prueba con un path variable de tipo UUID. */
    @RestController
    static class PathProbeController {

      @GetMapping("/probe/path/{id}")
      void probe(@PathVariable UUID id) {}
    }

    /** Controller de prueba con body validado. */
    @RestController
    static class BodyProbeController {

      @PostMapping("/probe/body")
      void probe(@Valid @RequestBody ProbeBody body) {}
    }

    /** Controller de prueba con una constraint directa sobre un parámetro. */
    @RestController
    static class MethodValidationProbeController {

      @GetMapping("/probe/method-validation")
      void probe(@RequestParam @NotBlank String name) {}
    }

    /** Controller de prueba que lanza una excepción no controlada para el fallback 500. */
    @RestController
    static class ErrorProbeController {

      @GetMapping("/error-probe")
      void probe() {
        throw new RuntimeException("error probe");
      }
    }
  }

  /** Body de prueba con una constraint para ejercitar la validación Bean Validation. */
  record ProbeBody(@NotBlank String name) {}

  /** Deserialización parcial del shape {@code ProblemDetail} para los asserts de los tests. */
  record ProblemDetailResponse(String code, String title, Integer status) {}

  /** Deserialización del shape de validación con el array de errores por campo. */
  record ValidationProblemDetailResponse(
      String code, String title, Integer status, List<FieldErrorResponse> errors) {}

  /** Deserialización de un error de campo de la validación. */
  record FieldErrorResponse(String code, String field, String message) {}
}
