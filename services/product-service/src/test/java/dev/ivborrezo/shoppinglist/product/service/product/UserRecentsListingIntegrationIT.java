package dev.ivborrezo.shoppinglist.product.service.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.ivborrezo.shoppinglist.product.service.common.ProductType;
import dev.ivborrezo.shoppinglist.product.service.product.dto.ProductReference;
import java.util.List;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Test de integración del listado de productos recientes sobre el endpoint {@code GET
 * /user-products/recents}.
 *
 * <p>Registra interacciones con productos base vía el endpoint de toggle de favoritos y verifica
 * que el listado devuelve los nombres resueltos según el tipo de producto y el orden de más
 * reciente a más antiguo.
 */
@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@AutoConfigureMockMvc
@TestConstructor(autowireMode = AutowireMode.ALL)
@Transactional
@Testcontainers
class UserRecentsListingIntegrationIT {

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

  private static final UUID OWNER_ID = UUID.fromString("aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee");

  private final MockMvc mockMvc;

  private final ObjectMapper objectMapper;

  UserRecentsListingIntegrationIT(MockMvc mockMvc, ObjectMapper objectMapper) {
    this.mockMvc = mockMvc;
    this.objectMapper = objectMapper;
  }

  /**
   * Lista los productos recientes de un usuario en orden de más reciente a más antiguo, resolviendo
   * el nombre de cada producto base en el idioma de la petición.
   */
  @Test
  void listRecents_returnsProductsOrderedByLastUsedAtDesc() throws Exception {
    markFavorite(1L);
    markFavorite(2L);
    markFavorite(3L);

    List<ProductReference> recents = getRecents("es");

    assertThat(recents).hasSize(3);
    assertThat(recents).extracting(ProductReference::productId).containsExactly(3L, 2L, 1L);
    assertThat(recents)
        .extracting(ProductReference::name)
        .containsExactly("Queso curado", "Yogur natural", "Leche entera");
    assertThat(recents).extracting(ProductReference::productType).containsOnly(ProductType.BASE);
  }

  /** Devuelve un listado vacío cuando el usuario no tiene ninguna interacción registrada. */
  @Test
  void listRecents_empty_returnsEmptyList() throws Exception {
    List<ProductReference> recents = getRecents(null);

    assertThat(recents).isEmpty();
  }

  /**
   * Re-marcar un producto ya reciente (tras desmarcarlo del favorito) actualiza su última
   * interacción y lo desplaza al inicio del listado sin eliminar su fila de reciente.
   */
  @Test
  void listRecents_toggleCombined_reMarkedProductMovesToTop() throws Exception {
    markFavorite(1L);
    markFavorite(2L);
    markFavorite(3L);
    assertThat(getRecents(null))
        .extracting(ProductReference::productId)
        .containsExactly(3L, 2L, 1L);

    markFavorite(1L);
    markFavorite(1L);

    List<ProductReference> recents = getRecents(null);
    assertThat(recents).extracting(ProductReference::productId).containsExactly(1L, 3L, 2L);
  }

  /**
   * Recupera el listado de productos recientes del propietario de los tests con el idioma indicado.
   *
   * @param acceptLanguage idioma de la petición en el que se resuelven los nombres de los productos
   *     base; {@code null} para omitir la cabecera
   * @return lista de referencias a producto devuelta por el endpoint
   * @throws Exception si la petición MockMvc o la deserialización fallan
   */
  private List<ProductReference> getRecents(String acceptLanguage) throws Exception {
    var request = get("/user-products/recents").param("ownerId", OWNER_ID.toString());
    if (acceptLanguage != null) {
      request = request.header("Accept-Language", acceptLanguage);
    }
    MvcResult result = mockMvc.perform(request).andExpect(status().isOk()).andReturn();

    return objectMapper.readValue(
        result.getResponse().getContentAsByteArray(),
        new TypeReference<List<ProductReference>>() {});
  }

  /**
   * Marca como favorito el producto base indicado vía el endpoint de toggle, registrando la
   * interacción en los recientes.
   *
   * @param productId identificador del producto base a marcar
   * @throws Exception si la petición MockMvc falla
   */
  private void markFavorite(Long productId) throws Exception {
    mockMvc
        .perform(
            post("/user-products/{id}/favorite", productId)
                .param("ownerId", OWNER_ID.toString())
                .param("productType", "BASE"))
        .andExpect(status().isOk());
  }
}
