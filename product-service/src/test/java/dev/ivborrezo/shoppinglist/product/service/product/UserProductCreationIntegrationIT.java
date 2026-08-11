package dev.ivborrezo.shoppinglist.product.service.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.ivborrezo.shoppinglist.product.service.common.CaloriesPerEnum;
import dev.ivborrezo.shoppinglist.product.service.common.UnitEnum;
import dev.ivborrezo.shoppinglist.product.service.product.dto.UserProductResponseDto;
import java.util.UUID;
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
 * Test de integración del endpoint {@code POST /user-products}.
 *
 * <p>Verifica la creación de productos de usuario con body mínimo, el copy-on-create desde un
 * producto base (con los valores del seed V7 localizados en el {@code Accept-Language} de la
 * petición), la precedencia de los valores propios sobre el snapshot del base, y la validación
 * condicional de campos obligatorios.
 */
@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@AutoConfigureMockMvc
@AutoConfigureTestEntityManager
@TestConstructor(autowireMode = AutowireMode.ALL)
@Transactional
@Testcontainers
class UserProductCreationIntegrationIT {

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

  private static final UUID OWNER_ID = UUID.fromString("aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee");

  private final MockMvc mockMvc;

  private final ObjectMapper objectMapper;

  private final TestEntityManager entityManager;

  UserProductCreationIntegrationIT(
      MockMvc mockMvc, ObjectMapper objectMapper, TestEntityManager entityManager) {
    this.mockMvc = mockMvc;
    this.objectMapper = objectMapper;
    this.entityManager = entityManager;
  }

  /**
   * Crea un producto de usuario con body mínimo y devuelve 201 con la cabecera {@code Location}.
   */
  @Test
  void createUserProduct_minimalBody_returns201AndLocation() throws Exception {
    String body =
        """
        {
          "ownerId": "%s",
          "name": "Leche entera",
          "defaultUnit": "L",
          "caloriesPer": "ML"
        }
        """
            .formatted(OWNER_ID);

    MvcResult result =
        mockMvc
            .perform(post("/user-products").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated())
            .andExpect(header().exists("Location"))
            .andReturn();

    UserProductResponseDto created =
        objectMapper.readValue(
            result.getResponse().getContentAsByteArray(), UserProductResponseDto.class);

    assertThat(created.name()).isEqualTo("Leche entera");
    assertThat(created.defaultUnit()).isEqualTo(UnitEnum.L);
    assertThat(created.caloriesPer()).isEqualTo(CaloriesPerEnum.ML);
    assertThat(created.ownerId()).isEqualTo(OWNER_ID);
    assertThat(created.basedOnBaseId()).isNull();
  }

  /**
   * Crea un producto de usuario a partir del producto base 1 del seed V7 y devuelve los campos
   * copiados del snapshot, con el nombre y la descripción localizados al español.
   */
  @Test
  void createUserProduct_withBasedOnBaseId_copiesBaseValuesInRequestLanguage() throws Exception {
    String body =
        """
        {
          "ownerId": "%s",
          "basedOnBaseId": 1
        }
        """
            .formatted(OWNER_ID);

    MvcResult result =
        mockMvc
            .perform(
                post("/user-products")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Accept-Language", "es")
                    .content(body))
            .andExpect(status().isCreated())
            .andExpect(header().exists("Location"))
            .andReturn();

    UserProductResponseDto created =
        objectMapper.readValue(
            result.getResponse().getContentAsByteArray(), UserProductResponseDto.class);

    assertThat(created.name()).isEqualTo("Leche entera");
    assertThat(created.description()).isEqualTo("Leche de vaca entera, sin desnatar");
    assertThat(created.categoryId()).isEqualTo(1L);
    assertThat(created.defaultUnit()).isEqualTo(UnitEnum.L);
    assertThat(created.calories()).isNull();
    assertThat(created.caloriesPer()).isEqualTo(CaloriesPerEnum.ML);
    assertThat(created.basedOnBaseId()).isEqualTo(1L);
  }

  /** Prefiere el {@code name} propio del body sobre el snapshot copiado del producto base. */
  @Test
  void createUserProduct_withBasedOnBaseIdAndOwnName_bodyNameWins() throws Exception {
    String body =
        """
        {
          "ownerId": "%s",
          "basedOnBaseId": 1,
          "name": "Mi marca de leche"
        }
        """
            .formatted(OWNER_ID);

    MvcResult result =
        mockMvc
            .perform(post("/user-products").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated())
            .andReturn();

    UserProductResponseDto created =
        objectMapper.readValue(
            result.getResponse().getContentAsByteArray(), UserProductResponseDto.class);

    assertThat(created.name()).isEqualTo("Mi marca de leche");
    assertThat(created.categoryId()).isEqualTo(1L);
    assertThat(created.defaultUnit()).isEqualTo(UnitEnum.L);
    assertThat(created.caloriesPer()).isEqualTo(CaloriesPerEnum.ML);
  }

  /** Rechaza con 400 el copy-on-create desde un producto base inexistente. */
  @Test
  void createUserProduct_withNonexistentBasedOnBaseId_returns400() throws Exception {
    String body =
        """
        {
          "ownerId": "%s",
          "basedOnBaseId": 999999
        }
        """
            .formatted(OWNER_ID);

    mockMvc
        .perform(post("/user-products").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest());
  }

  /** Rechaza con 400 un {@code ownerId} que no es un UUID válido. */
  @Test
  void createUserProduct_withMalformedOwnerId_returns400() throws Exception {
    String body =
        """
        {
          "ownerId": "no-es-un-uuid",
          "name": "X",
          "defaultUnit": "UNIT",
          "caloriesPer": "G"
        }
        """;

    mockMvc
        .perform(post("/user-products").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest());
  }

  /**
   * Rechaza con 400 la ausencia de {@code name} cuando no hay {@code basedOnBaseId} que lo copie.
   */
  @Test
  void createUserProduct_withoutNameAndWithoutBasedOnBaseId_returns400() throws Exception {
    String body =
        """
        {
          "ownerId": "%s",
          "defaultUnit": "UNIT",
          "caloriesPer": "G"
        }
        """
            .formatted(OWNER_ID);

    mockMvc
        .perform(post("/user-products").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest());
  }
}
