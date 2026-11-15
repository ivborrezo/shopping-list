package dev.ivborrezo.shoppinglist.product.service.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.ivborrezo.shoppinglist.testsupport.TestAuth;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.TestConstructor.AutowireMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Test de integración de la autorización por rol del catálogo global.
 *
 * <p>Comprueba que un usuario autenticado solo con rol {@code USER} recibe {@code 403
 * ACCESS_DENIED} en las mutaciones de categorías y productos base, que requieren el rol {@code
 * ADMIN}.
 */
@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@AutoConfigureMockMvc
@TestConstructor(autowireMode = AutowireMode.ALL)
@Testcontainers
class CatalogAuthorizationIntegrationIT {

  @Container @ServiceConnection
  static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

  private static final String CATEGORY_BODY =
      """
      {
        "code": "forbidden_probe",
        "isActive": true,
        "translations": [{"locale": "es", "name": "Probe"}]
      }
      """;

  private static final String BASE_PRODUCT_BODY =
      """
      {
        "code": "forbidden_probe",
        "categoryId": "11111111-1111-4111-8111-111111111111",
        "defaultUnit": "UNIT",
        "caloriesPer": "G",
        "isActive": true,
        "translations": [{"locale": "es", "name": "Probe"}]
      }
      """;

  private final MockMvc mockMvc;

  CatalogAuthorizationIntegrationIT(MockMvc mockMvc) {
    this.mockMvc = mockMvc;
  }

  /** Rechaza la creación de categorías a un usuario sin rol ADMIN. */
  @Test
  void createCategory_withUserRole_returns403AccessDenied() throws Exception {
    assertAccessDenied(
        mockMvc.perform(
            post("/categories")
                .with(TestAuth.user1())
                .contentType(MediaType.APPLICATION_JSON)
                .content(CATEGORY_BODY)));
  }

  /** Rechaza la creación de productos base a un usuario sin rol ADMIN. */
  @Test
  void createBaseProduct_withUserRole_returns403AccessDenied() throws Exception {
    assertAccessDenied(
        mockMvc.perform(
            post("/base-products")
                .with(TestAuth.user1())
                .contentType(MediaType.APPLICATION_JSON)
                .content(BASE_PRODUCT_BODY)));
  }

  /** Rechaza la edición de productos base a un usuario sin rol ADMIN. */
  @Test
  void patchBaseProduct_withUserRole_returns403AccessDenied() throws Exception {
    assertAccessDenied(
        mockMvc.perform(
            patch("/base-products/" + UUID.randomUUID())
                .with(TestAuth.user1())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"isActive\": false}")));
  }

  /** Rechaza el borrado de productos base a un usuario sin rol ADMIN. */
  @Test
  void deleteBaseProduct_withUserRole_returns403AccessDenied() throws Exception {
    assertAccessDenied(
        mockMvc.perform(delete("/base-products/" + UUID.randomUUID()).with(TestAuth.user1())));
  }

  private void assertAccessDenied(ResultActions result) throws Exception {
    result
        .andExpect(status().isForbidden())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
  }
}
