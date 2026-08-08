package dev.ivborrezo.shoppinglist.product.service.category;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.ivborrezo.shoppinglist.product.service.category.dto.CategoryResponseDto;
import dev.ivborrezo.shoppinglist.product.service.category.entity.Category;
import java.time.Instant;
import java.util.List;
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
 * Test de integración del endpoint {@code GET /categories}.
 *
 * <p>Ejecuta la petición HTTP real contra el {@link MockMvc} de Spring, atravesando toda la pila
 * ({@code DispatcherServlet → CategoryController → CategoryService → CategoryRepository →
 * PostgreSQL}) sin mocks de ninguna capa intermedia. La base de datos es un contenedor PostgreSQL
 * efímero arrancado por Testcontainers y cableado vía {@link ServiceConnection}, sobre el que se
 * aplican las migraciones Flyway reales del servicio (V1 DDL + V2 seed) al arrancar el contexto.
 *
 * <p>El contenedor se declara estático para que viva a nivel de JVM/Surefire (patrón singleton
 * container de Testcontainers) y se arranque una sola vez para toda la ejecución de tests de la
 * clase. El aislamiento de datos entre métodos se garantiza por el {@link
 * Transactional @Transactional} a nivel de clase, que revierte cada test en su propia transacción
 * sin tocar el ciclo de vida del contenedor. La distinción "aislados por test" de ADR-008 se
 * satisface por tanto a nivel de datos, no de contenedor.
 */
@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@AutoConfigureMockMvc
@AutoConfigureTestEntityManager
@TestConstructor(autowireMode = AutowireMode.ALL)
@Transactional
@Testcontainers
class CategoryIntegrationIT {

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

  private final MockMvc mockMvc;

  private final ObjectMapper objectMapper;

  private final TestEntityManager entityManager;

  /**
   * Inyecta las dependencias de test por constructor, sin {@code @Autowired} por campo, coherente
   * con la convención del resto del monorepo (código de producción y tests).
   *
   * @param mockMvc cliente MockMvc contra el DispatcherServlet real
   * @param objectMapper mapper Jackson para deserializar el body de las respuestas HTTP
   * @param entityManager gestor JPA para inserciones ad hoc dentro de la transacción del test
   */
  CategoryIntegrationIT(
      MockMvc mockMvc, ObjectMapper objectMapper, TestEntityManager entityManager) {
    this.mockMvc = mockMvc;
    this.objectMapper = objectMapper;
    this.entityManager = entityManager;
  }

  /**
   * Valida que {@code GET /categories} devuelve las diez categorías activas del seed V2, con sus
   * campos estructurales correctos.
   *
   * @throws Exception si la petición MockMvc o la deserialización fallan
   */
  @Test
  void getCategories_returnsAllTenActiveSeededCategories() throws Exception {
    MvcResult result = mockMvc.perform(get("/categories")).andExpect(status().isOk()).andReturn();

    List<CategoryResponseDto> categories =
        objectMapper.readValue(
            result.getResponse().getContentAsByteArray(),
            new TypeReference<List<CategoryResponseDto>>() {});

    assertThat(categories).hasSize(10);
    assertThat(categories)
        .extracting(CategoryResponseDto::code)
        .containsExactlyInAnyOrder(
            "dairy",
            "bakery",
            "produce",
            "meat",
            "fish",
            "pantry",
            "beverages",
            "frozen",
            "household",
            "personal_care");
    assertThat(categories).extracting(CategoryResponseDto::isActive).containsOnly(true);
    assertThat(categories).allMatch(category -> category.id() != null);
  }

  /**
   * Valida que el filtro de categorías activas excluye una categoría marcada inactiva de forma ad
   * hoc para este test, sin alterar el seed Flyway de producción.
   *
   * <p>La categoría sintética se inserta dentro de la transacción del test y se revierte
   * automáticamente al finalizar, sin contaminar otros métodos ni persistir tras la ejecución.
   *
   * @throws Exception si la petición MockMvc o la deserialización fallan
   */
  @Test
  void getCategories_excludesAdHocInactiveCategoryNotFromSeed() throws Exception {
    Category adHocInactive = new Category();
    adHocInactive.setCode("_test_inactive_ad_hoc");
    adHocInactive.setIsActive(false);
    entityManager.persistAndFlush(adHocInactive);

    MvcResult result = mockMvc.perform(get("/categories")).andExpect(status().isOk()).andReturn();

    List<CategoryResponseDto> categories =
        objectMapper.readValue(
            result.getResponse().getContentAsByteArray(),
            new TypeReference<List<CategoryResponseDto>>() {});

    assertThat(categories).hasSize(10);
    assertThat(categories)
        .extracting(CategoryResponseDto::code)
        .doesNotContain("_test_inactive_ad_hoc");
  }

  /**
   * Valida que las columnas de auditoría {@code created_at} y {@code updated_at} se poblaron al
   * insertar las categorías del seed, según la convención de timestamps de auditoría del monorepo
   * (TIMESTAMPTZ en PostgreSQL, {@link Instant} en JPA).
   *
   * <p>Las categorías del seed V2 se insertan por Flyway, que no pasa por el {@code
   * AuditingEntityListener} de JPA; los timestamps provienen del {@code DEFAULT CURRENT_TIMESTAMP}
   * declarado en V1. La verificación se hace por tanto sobre la entidad persistida leída vía {@link
   * TestEntityManager}, no sobre el DTO de respuesta (cuya superficie no incluye campos de
   * auditoría por contrato).
   */
  @Test
  void getCategories_auditingTimestampsArePopulated() {
    Category sample = entityManager.find(Category.class, 1L);

    assertThat(sample.getCreatedAt()).isNotNull();
    assertThat(sample.getUpdatedAt()).isNotNull();
    assertThat(sample.getCreatedAt()).isBeforeOrEqualTo(sample.getUpdatedAt());
  }
}
