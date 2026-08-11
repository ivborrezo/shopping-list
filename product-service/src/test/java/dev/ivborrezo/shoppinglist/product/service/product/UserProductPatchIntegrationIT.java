package dev.ivborrezo.shoppinglist.product.service.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.ivborrezo.shoppinglist.product.service.product.dto.UserProductResponseDto;
import dev.ivborrezo.shoppinglist.product.service.product.entity.UserProduct;
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
 * Test de integración del endpoint {@code PATCH /user-products/{id}}.
 *
 * <p>Verifica la edición parcial de un producto de usuario: actualización de campos con propietario
 * coincidente, rechazo con propietario no coincidente, inmutabilidad de {@code basedOnBaseId} y
 * {@code 404} para productos inexistentes.
 */
@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@AutoConfigureMockMvc
@AutoConfigureTestEntityManager
@TestConstructor(autowireMode = AutowireMode.ALL)
@Transactional
@Testcontainers
class UserProductPatchIntegrationIT {

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

  private static final UUID OWNER_ID = UUID.fromString("aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee");

  private static final UUID OTHER_OWNER_ID =
      UUID.fromString("aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeef");

  private final MockMvc mockMvc;

  private final ObjectMapper objectMapper;

  private final TestEntityManager entityManager;

  UserProductPatchIntegrationIT(
      MockMvc mockMvc, ObjectMapper objectMapper, TestEntityManager entityManager) {
    this.mockMvc = mockMvc;
    this.objectMapper = objectMapper;
    this.entityManager = entityManager;
  }

  /** Actualiza los campos no nulos del body y devuelve el DTO con los nuevos valores. */
  @Test
  void patchUserProduct_withMatchingOwner_updatesFields() throws Exception {
    UserProduct product = buildProduct(OWNER_ID, "Original", null);
    persist(product);

    String body =
        """
        {
          "ownerId": "%s",
          "name": "Actualizado",
          "calories": 200
        }
        """
            .formatted(OWNER_ID);

    MvcResult result =
        mockMvc
            .perform(
                patch("/user-products/" + product.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isOk())
            .andReturn();

    UserProductResponseDto updated =
        objectMapper.readValue(
            result.getResponse().getContentAsByteArray(), UserProductResponseDto.class);

    assertThat(updated.id()).isEqualTo(product.getId());
    assertThat(updated.ownerId()).isEqualTo(OWNER_ID);
    assertThat(updated.name()).isEqualTo("Actualizado");
    assertThat(updated.calories()).isEqualTo(200);
    assertThat(updated.isActive()).isTrue();
  }

  /**
   * Rechaza con 403 la actualización cuando el propietario del body no coincide con el del
   * producto.
   */
  @Test
  void patchUserProduct_withMismatchedOwner_returns403() throws Exception {
    UserProduct product = buildProduct(OWNER_ID, "Original", null);
    persist(product);

    String body =
        """
        {
          "ownerId": "%s",
          "name": "Intruso"
        }
        """
            .formatted(OTHER_OWNER_ID);

    mockMvc
        .perform(
            patch("/user-products/" + product.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isForbidden());
  }

  /** Ignora en silencio el campo inmutable {@code basedOnBaseId} enviado en el body. */
  @Test
  void patchUserProduct_withBasedOnBaseId_ignoresImmutableField() throws Exception {
    UserProduct product = buildProduct(OWNER_ID, "Original", null);
    product.setBasedOnBaseId(1L);
    persist(product);

    String body =
        """
        {
          "ownerId": "%s",
          "basedOnBaseId": 2,
          "name": "Renombrado"
        }
        """
            .formatted(OWNER_ID);

    MvcResult result =
        mockMvc
            .perform(
                patch("/user-products/" + product.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isOk())
            .andReturn();

    UserProductResponseDto updated =
        objectMapper.readValue(
            result.getResponse().getContentAsByteArray(), UserProductResponseDto.class);

    assertThat(updated.name()).isEqualTo("Renombrado");
    assertThat(updated.basedOnBaseId()).isEqualTo(1L);
  }

  /** Devuelve 404 cuando el identificador de producto de usuario no existe. */
  @Test
  void patchUserProduct_nonexistentId_returns404() throws Exception {
    String body =
        """
        {
          "ownerId": "%s",
          "name": "X"
        }
        """
            .formatted(OWNER_ID);

    mockMvc
        .perform(
            patch("/user-products/99999").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isNotFound());
  }

  /**
   * Persiste el producto indicado dentro de la transacción del test y fuerza el {@code flush} para
   * que JPA asigne el identificador generado.
   *
   * @param product producto de usuario a insertar
   */
  private void persist(UserProduct product) {
    entityManager.persist(product);
    entityManager.flush();
  }

  /**
   * Construye un producto de usuario activo con los campos mínimos para los tests.
   *
   * @param ownerId propietario del producto
   * @param name nombre del producto
   * @param calories calorías del producto; {@code null} para dejarlas sin valor
   * @return entidad {@link UserProduct} con los valores indicados
   */
  private UserProduct buildProduct(UUID ownerId, String name, Integer calories) {
    UserProduct product = new UserProduct();
    product.setOwnerId(ownerId);
    product.setName(name);
    product.setDescription("Descripción de prueba");
    product.setCategoryId(1L);
    product.setDefaultUnit("UNIT");
    product.setCalories(calories);
    product.setCaloriesPer("G");
    product.setShareWithListMembers(false);
    product.setShareWithFriends(false);
    product.setIsActive(true);
    return product;
  }
}
