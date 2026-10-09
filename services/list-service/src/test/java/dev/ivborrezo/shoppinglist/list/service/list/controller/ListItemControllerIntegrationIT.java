package dev.ivborrezo.shoppinglist.list.service.list.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.ivborrezo.shoppinglist.list.service.common.BusinessException;
import dev.ivborrezo.shoppinglist.list.service.common.ErrorCode;
import dev.ivborrezo.shoppinglist.list.service.common.ProductType;
import dev.ivborrezo.shoppinglist.list.service.list.client.ProductCatalogClient;
import dev.ivborrezo.shoppinglist.list.service.list.dto.ListItemResponse;
import dev.ivborrezo.shoppinglist.list.service.list.entity.ListItem;
import dev.ivborrezo.shoppinglist.list.service.list.entity.ShoppingList;
import dev.ivborrezo.shoppinglist.list.service.list.repository.ListItemRepository;
import dev.ivborrezo.shoppinglist.list.service.list.repository.ShoppingListRepository;
import java.time.Instant;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

/**
 * Test de integración de los endpoints REST de {@code /lists/{id}/items}.
 *
 * <p>Arranca el contexto completo contra PostgreSQL real (Testcontainers), sustituye el catálogo de
 * productos por un mock y ejercita vía MockMvc el contrato de {@code api-contract.yaml}: alta con
 * snapshot y {@code Location}, validación de la referencia y del tipo de producto, unicidad,
 * propiedad, cambio de estado de compra y borrado. Comprueba además que cada mutación refresca la
 * actividad de la lista padre.
 */
@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@AutoConfigureMockMvc
@AutoConfigureTestEntityManager
@TestConstructor(autowireMode = AutowireMode.ALL)
@Transactional
@Testcontainers
class ListItemControllerIntegrationIT {

  @Container @ServiceConnection
  static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

  private static final UUID OWNER_ID = UUID.fromString("aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee");

  private static final UUID OTHER_OWNER_ID =
      UUID.fromString("aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeef");

  @MockitoBean private ProductCatalogClient productCatalogClient;

  private final MockMvc mockMvc;

  private final ObjectMapper objectMapper;

  private final TestEntityManager entityManager;

  private final ShoppingListRepository shoppingListRepository;

  private final ListItemRepository listItemRepository;

  /**
   * Inyecta las dependencias de test por constructor, sin {@code @Autowired} por campo, coherente
   * con la convención del resto del monorepo.
   *
   * @param mockMvc cliente MockMvc contra el DispatcherServlet real
   * @param objectMapper mapper Jackson para deserializar el body de las respuestas HTTP
   * @param entityManager gestor JPA para sembrar y limpiar el contexto de persistencia
   * @param shoppingListRepository repositorio de listas para verificar persistencia
   * @param listItemRepository repositorio de ítems para sembrar datos de soporte
   */
  ListItemControllerIntegrationIT(
      MockMvc mockMvc,
      ObjectMapper objectMapper,
      TestEntityManager entityManager,
      ShoppingListRepository shoppingListRepository,
      ListItemRepository listItemRepository) {
    this.mockMvc = mockMvc;
    this.objectMapper = objectMapper;
    this.entityManager = entityManager;
    this.shoppingListRepository = shoppingListRepository;
    this.listItemRepository = listItemRepository;
  }

  /**
   * Añade un producto con body válido y devuelve 201 con el snapshot, el estado inicial y la
   * cabecera {@code Location}, refrescando la actividad de la lista.
   *
   * @throws Exception si la petición HTTP simulada falla
   */
  @Test
  void addItem_withValidBody_returns201AndRefreshesListActivity() throws Exception {
    ShoppingList list = seedList(OWNER_ID, "Compra semanal");
    Instant before = list.getUpdatedAt();
    Thread.sleep(10);
    UUID productId = UUID.randomUUID();
    when(productCatalogClient.resolveDisplayName(ProductType.BASE, productId)).thenReturn("Leche");

    String body =
        """
        {
          "productId": "%s",
          "productType": "BASE"
        }
        """
            .formatted(productId);

    MvcResult result =
        mockMvc
            .perform(
                post("/lists/" + list.getPublicId() + "/items")
                    .param("ownerId", OWNER_ID.toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isCreated())
            .andExpect(header().exists("Location"))
            .andReturn();

    ListItemResponse created =
        objectMapper.readValue(
            result.getResponse().getContentAsByteArray(), ListItemResponse.class);

    assertThat(created.id()).isNotNull();
    assertThat(created.productId()).isEqualTo(productId);
    assertThat(created.productType()).isEqualTo(ProductType.BASE);
    assertThat(created.displayName()).isEqualTo("Leche");
    assertThat(created.purchased()).isFalse();
    assertThat(result.getResponse().getHeader("Location"))
        .isEqualTo("/lists/" + list.getPublicId() + "/items/" + created.id());

    entityManager.flush();
    entityManager.clear();
    ShoppingList reloaded = shoppingListRepository.findByPublicId(list.getPublicId()).orElseThrow();
    assertThat(reloaded.getUpdatedAt()).isAfter(before);
  }

  /** Devuelve 400 con {@code INVALID_PRODUCT_TYPE} cuando el tipo de producto no está soportado. */
  @Test
  void addItem_withInvalidProductType_returns400() throws Exception {
    ShoppingList list = seedList(OWNER_ID, "Compra semanal");

    String body =
        """
        {
          "productId": "%s",
          "productType": "OTRO"
        }
        """
            .formatted(UUID.randomUUID());

    mockMvc
        .perform(
            post("/lists/" + list.getPublicId() + "/items")
                .param("ownerId", OWNER_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("INVALID_PRODUCT_TYPE"));
  }

  /** Devuelve 400 con {@code VALIDATION_FAILED} cuando falta {@code productId}. */
  @Test
  void addItem_withoutProductId_returnsValidationProblemDetail() throws Exception {
    ShoppingList list = seedList(OWNER_ID, "Compra semanal");

    String body =
        """
        {
          "productType": "BASE"
        }
        """;

    mockMvc
        .perform(
            post("/lists/" + list.getPublicId() + "/items")
                .param("ownerId", OWNER_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.errors[0].field").value("productId"));
  }

  /** Devuelve 400 con {@code VALIDATION_FAILED} cuando falta {@code productType}. */
  @Test
  void addItem_withoutProductType_returnsValidationProblemDetail() throws Exception {
    ShoppingList list = seedList(OWNER_ID, "Compra semanal");

    String body =
        """
        {
          "productId": "%s"
        }
        """
            .formatted(UUID.randomUUID());

    mockMvc
        .perform(
            post("/lists/" + list.getPublicId() + "/items")
                .param("ownerId", OWNER_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.errors[0].field").value("productType"));
  }

  /** Devuelve 409 con {@code DUPLICATE_LIST_ITEM} cuando el producto ya está en la lista. */
  @Test
  void addItem_withDuplicateProduct_returns409() throws Exception {
    ShoppingList list = seedList(OWNER_ID, "Compra semanal");
    UUID productId = UUID.randomUUID();
    seedItem(list.getId(), productId, ProductType.BASE, "Leche", false);
    when(productCatalogClient.resolveDisplayName(ProductType.BASE, productId)).thenReturn("Leche");

    String body =
        """
        {
          "productId": "%s",
          "productType": "BASE"
        }
        """
            .formatted(productId);

    mockMvc
        .perform(
            post("/lists/" + list.getPublicId() + "/items")
                .param("ownerId", OWNER_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isConflict())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("DUPLICATE_LIST_ITEM"));
  }

  /** Devuelve 404 con {@code LIST_NOT_FOUND} cuando la lista no existe. */
  @Test
  void addItem_withNonExistentList_returns404() throws Exception {
    String body =
        """
        {
          "productId": "%s",
          "productType": "BASE"
        }
        """
            .formatted(UUID.randomUUID());

    mockMvc
        .perform(
            post("/lists/" + UUID.randomUUID() + "/items")
                .param("ownerId", OWNER_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isNotFound())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("LIST_NOT_FOUND"));
  }

  /** Devuelve 403 con {@code OWNER_MISMATCH} cuando el solicitante no es el propietario. */
  @Test
  void addItem_withOtherOwner_returns403() throws Exception {
    ShoppingList list = seedList(OWNER_ID, "Compra semanal");

    String body =
        """
        {
          "productId": "%s",
          "productType": "BASE"
        }
        """
            .formatted(UUID.randomUUID());

    mockMvc
        .perform(
            post("/lists/" + list.getPublicId() + "/items")
                .param("ownerId", OTHER_OWNER_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isForbidden())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("OWNER_MISMATCH"));
  }

  /** Devuelve 400 con {@code INVALID_PRODUCT_REFERENCE} cuando el catálogo rechaza el producto. */
  @Test
  void addItem_withInvalidProductReference_returns400() throws Exception {
    ShoppingList list = seedList(OWNER_ID, "Compra semanal");
    when(productCatalogClient.resolveDisplayName(any(), any()))
        .thenThrow(new BusinessException(ErrorCode.INVALID_PRODUCT_REFERENCE));

    String body =
        """
        {
          "productId": "%s",
          "productType": "BASE"
        }
        """
            .formatted(UUID.randomUUID());

    mockMvc
        .perform(
            post("/lists/" + list.getPublicId() + "/items")
                .param("ownerId", OWNER_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("INVALID_PRODUCT_REFERENCE"));
  }

  /** Devuelve 503 con {@code PRODUCT_SERVICE_UNAVAILABLE} cuando el catálogo no responde. */
  @Test
  void addItem_withProductServiceUnavailable_returns503() throws Exception {
    ShoppingList list = seedList(OWNER_ID, "Compra semanal");
    when(productCatalogClient.resolveDisplayName(any(), any()))
        .thenThrow(new BusinessException(ErrorCode.PRODUCT_SERVICE_UNAVAILABLE));

    String body =
        """
        {
          "productId": "%s",
          "productType": "BASE"
        }
        """
            .formatted(UUID.randomUUID());

    mockMvc
        .perform(
            post("/lists/" + list.getPublicId() + "/items")
                .param("ownerId", OWNER_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isServiceUnavailable())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("PRODUCT_SERVICE_UNAVAILABLE"));
  }

  /**
   * Marca un ítem existente como comprado, devuelve el estado actualizado y refresca la actividad
   * de la lista.
   *
   * @throws Exception si la petición HTTP simulada falla
   */
  @Test
  void updateItemPurchased_withValidBody_returns200AndRefreshesListActivity() throws Exception {
    ShoppingList list = seedList(OWNER_ID, "Compra semanal");
    ListItem item = seedItem(list.getId(), UUID.randomUUID(), ProductType.BASE, "Leche", false);
    Instant before = list.getUpdatedAt();
    Thread.sleep(10);

    String body =
        """
        {
          "purchased": true
        }
        """;

    MvcResult result =
        mockMvc
            .perform(
                patch("/lists/" + list.getPublicId() + "/items/" + item.getPublicId())
                    .param("ownerId", OWNER_ID.toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isOk())
            .andReturn();

    ListItemResponse updated =
        objectMapper.readValue(
            result.getResponse().getContentAsByteArray(), ListItemResponse.class);

    assertThat(updated.id()).isEqualTo(item.getPublicId());
    assertThat(updated.displayName()).isEqualTo("Leche");
    assertThat(updated.purchased()).isTrue();

    entityManager.flush();
    entityManager.clear();
    assertThat(listItemRepository.findByPublicId(item.getPublicId()).orElseThrow().getPurchased())
        .isTrue();
    ShoppingList reloaded = shoppingListRepository.findByPublicId(list.getPublicId()).orElseThrow();
    assertThat(reloaded.getUpdatedAt()).isAfter(before);
  }

  /** Devuelve 404 con {@code LIST_ITEM_NOT_FOUND} cuando el ítem no existe. */
  @Test
  void updateItemPurchased_withNonExistentItem_returns404() throws Exception {
    ShoppingList list = seedList(OWNER_ID, "Compra semanal");

    String body =
        """
        {
          "purchased": true
        }
        """;

    mockMvc
        .perform(
            patch("/lists/" + list.getPublicId() + "/items/" + UUID.randomUUID())
                .param("ownerId", OWNER_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isNotFound())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("LIST_ITEM_NOT_FOUND"));
  }

  /**
   * Elimina un ítem existente con 204, lo deja de encontrar y refresca la actividad de la lista.
   *
   * @throws Exception si la petición HTTP simulada falla
   */
  @Test
  void removeItem_withExistingItem_returns204AndRefreshesListActivity() throws Exception {
    ShoppingList list = seedList(OWNER_ID, "Compra semanal");
    ListItem item = seedItem(list.getId(), UUID.randomUUID(), ProductType.BASE, "Leche", false);
    Instant before = list.getUpdatedAt();
    Thread.sleep(10);

    mockMvc
        .perform(
            delete("/lists/" + list.getPublicId() + "/items/" + item.getPublicId())
                .param("ownerId", OWNER_ID.toString()))
        .andExpect(status().isNoContent());

    entityManager.flush();
    entityManager.clear();
    assertThat(listItemRepository.findByPublicId(item.getPublicId())).isEmpty();
    ShoppingList reloaded = shoppingListRepository.findByPublicId(list.getPublicId()).orElseThrow();
    assertThat(reloaded.getUpdatedAt()).isAfter(before);
  }

  /** Devuelve 404 con {@code LIST_ITEM_NOT_FOUND} cuando el ítem a eliminar no existe. */
  @Test
  void removeItem_withNonExistentItem_returns404() throws Exception {
    ShoppingList list = seedList(OWNER_ID, "Compra semanal");

    mockMvc
        .perform(
            delete("/lists/" + list.getPublicId() + "/items/" + UUID.randomUUID())
                .param("ownerId", OWNER_ID.toString()))
        .andExpect(status().isNotFound())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("LIST_ITEM_NOT_FOUND"));
  }

  /**
   * Siembra una lista, limpia el contexto de persistencia y devuelve la entidad con sus
   * identificadores y marcas de auditoría asignados.
   *
   * @param ownerId propietario de la lista
   * @param name nombre de la lista
   * @return lista persistida
   */
  private ShoppingList seedList(UUID ownerId, String name) {
    ShoppingList list = new ShoppingList();
    list.setOwnerId(ownerId);
    list.setName(name);
    shoppingListRepository.saveAndFlush(list);
    entityManager.clear();
    return list;
  }

  /**
   * Siembra un ítem de la lista indicada y limpia el contexto de persistencia.
   *
   * @param listId id interno de la lista propietaria
   * @param productId identificador público del producto referenciado
   * @param productType tipo de producto referenciado
   * @param displayName snapshot del nombre del producto
   * @param purchased si el ítem está comprado
   * @return ítem persistido
   */
  private ListItem seedItem(
      Long listId, UUID productId, ProductType productType, String displayName, boolean purchased) {
    ListItem item = new ListItem();
    item.setListId(listId);
    item.setProductId(productId);
    item.setProductType(productType);
    item.setDisplayName(displayName);
    item.setPurchased(purchased);
    listItemRepository.saveAndFlush(item);
    entityManager.clear();
    return item;
  }
}
