package dev.ivborrezo.shoppinglist.list.service.list.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.ivborrezo.shoppinglist.list.service.common.ProductType;
import dev.ivborrezo.shoppinglist.list.service.list.dto.ShoppingListResponse;
import dev.ivborrezo.shoppinglist.list.service.list.entity.ListItem;
import dev.ivborrezo.shoppinglist.list.service.list.entity.ShoppingList;
import dev.ivborrezo.shoppinglist.list.service.list.repository.ListItemRepository;
import dev.ivborrezo.shoppinglist.list.service.list.repository.ShoppingListRepository;
import dev.ivborrezo.shoppinglist.testsupport.TestAuth;
import dev.ivborrezo.shoppinglist.testsupport.TestUsers;
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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

/**
 * Test de integración de la verificación de propiedad sobre las operaciones de listas e ítems.
 *
 * <p>Ejercita vía MockMvc el contrato de {@code api-contract.yaml}: las operaciones sobre una lista
 * existente solo proceden cuando el usuario autenticado es su propietario y responden {@code 403
 * OWNER_MISMATCH} en caso contrario; el recurso inexistente prevalece como {@code 404
 * LIST_NOT_FOUND} y las peticiones sin credenciales se rechazan con {@code 401 UNAUTHENTICATED}.
 */
@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@AutoConfigureMockMvc
@AutoConfigureTestEntityManager
@TestConstructor(autowireMode = AutowireMode.ALL)
@Transactional
@Testcontainers
class ListOwnershipIntegrationIT {

  @Container @ServiceConnection
  static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

  private static final UUID OWNER_ID = TestUsers.USER_1;

  private static final UUID OTHER_OWNER_ID = TestUsers.USER_2;

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
   * @param entityManager gestor JPA para inserciones ad hoc dentro de la transacción del test
   * @param shoppingListRepository repositorio de listas para verificar persistencia
   * @param listItemRepository repositorio de ítems para insertar datos de soporte
   */
  ListOwnershipIntegrationIT(
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

  /** Rechaza con 403 y {@code OWNER_MISMATCH} el renombrado por un usuario distinto. */
  @Test
  void patchList_withOtherOwner_returns403AndKeepsName() throws Exception {
    ShoppingList list = buildList(OWNER_ID, "Nombre antiguo");
    persist(list);

    String body =
        """
        {
          "name": "Nombre nuevo"
        }
        """;

    mockMvc
        .perform(
            patch("/lists/" + list.getPublicId())
                .with(TestAuth.user2())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isForbidden())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("OWNER_MISMATCH"));

    entityManager.refresh(list);
    assertThat(list.getName()).isEqualTo("Nombre antiguo");
  }

  /** Permite el renombrado cuando el usuario autenticado es el propietario almacenado. */
  @Test
  void patchList_withMatchingOwner_returns200AndNewName() throws Exception {
    ShoppingList list = buildList(OWNER_ID, "Nombre antiguo");
    persist(list);

    String body =
        """
        {
          "name": "Nombre nuevo"
        }
        """;

    MvcResult result =
        mockMvc
            .perform(
                patch("/lists/" + list.getPublicId())
                    .with(TestAuth.user1())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isOk())
            .andReturn();

    ShoppingListResponse updated =
        objectMapper.readValue(
            result.getResponse().getContentAsByteArray(), ShoppingListResponse.class);

    assertThat(updated.name()).isEqualTo("Nombre nuevo");
  }

  /** Devuelve 404 antes que 403 cuando la lista a renombrar no existe. */
  @Test
  void patchList_withNonExistentId_returns404RegardlessOfOwner() throws Exception {
    String body =
        """
        {
          "name": "Nombre nuevo"
        }
        """;

    mockMvc
        .perform(
            patch("/lists/" + UUID.randomUUID())
                .with(TestAuth.user2())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isNotFound())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("LIST_NOT_FOUND"));
  }

  /** Rechaza con 403 el borrado por un usuario distinto y conserva la lista. */
  @Test
  void deleteList_withOtherOwner_returns403AndKeepsList() throws Exception {
    ShoppingList list = buildList(OWNER_ID, "Ajena");
    persist(list);

    mockMvc
        .perform(delete("/lists/" + list.getPublicId()).with(TestAuth.user2()))
        .andExpect(status().isForbidden())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("OWNER_MISMATCH"));

    assertThat(shoppingListRepository.findByPublicId(list.getPublicId())).isPresent();
  }

  /** Elimina la lista y sus ítems con 204 cuando el usuario autenticado es su propietario. */
  @Test
  void deleteList_withMatchingOwner_returns204AndRemovesList() throws Exception {
    ShoppingList list = buildList(OWNER_ID, "Para borrar");
    persist(list);
    ListItem item = buildItem(list.getId(), UUID.randomUUID(), ProductType.BASE, "Leche", false);
    persist(item);

    mockMvc
        .perform(delete("/lists/" + list.getPublicId()).with(TestAuth.user1()))
        .andExpect(status().isNoContent());

    assertThat(shoppingListRepository.findByPublicId(list.getPublicId())).isEmpty();
    assertThat(listItemRepository.findByPublicId(item.getPublicId())).isEmpty();
  }

  /** Devuelve 404 antes que 403 cuando la lista a eliminar no existe. */
  @Test
  void deleteList_withNonExistentId_returns404RegardlessOfOwner() throws Exception {
    mockMvc
        .perform(delete("/lists/" + UUID.randomUUID()).with(TestAuth.user2()))
        .andExpect(status().isNotFound())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("LIST_NOT_FOUND"));
  }

  /** Rechaza con 401 la lectura por identificador sin credenciales. */
  @Test
  void getListById_withoutCredentials_returns401Unauthenticated() throws Exception {
    ShoppingList list = buildList(OWNER_ID, "Lista privada");
    persist(list);

    mockMvc
        .perform(get("/lists/" + list.getPublicId()))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
  }

  /** Rechaza con 403 y {@code OWNER_MISMATCH} la lectura de una lista ajena. */
  @Test
  void getListById_withOtherOwner_returns403OwnerMismatch() throws Exception {
    ShoppingList list = buildList(OWNER_ID, "Lista privada");
    persist(list);

    mockMvc
        .perform(get("/lists/" + list.getPublicId()).with(TestAuth.user2()))
        .andExpect(status().isForbidden())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("OWNER_MISMATCH"));
  }

  /** Devuelve el detalle de una lista propia y deja de aplicar la lectura abierta anterior. */
  @Test
  void getListById_withMatchingOwner_returns200() throws Exception {
    ShoppingList list = buildList(OWNER_ID, "Mi lista");
    persist(list);

    MvcResult result =
        mockMvc
            .perform(get("/lists/" + list.getPublicId()).with(TestAuth.user1()))
            .andExpect(status().isOk())
            .andReturn();

    ShoppingListResponse response =
        objectMapper.readValue(
            result.getResponse().getContentAsByteArray(), ShoppingListResponse.class);

    assertThat(response.id()).isEqualTo(list.getPublicId());
    assertThat(response.ownerId()).isEqualTo(OWNER_ID);
    assertThat(response.name()).isEqualTo("Mi lista");
  }

  /** Devuelve 404 antes que 403 cuando la lista leída no existe. */
  @Test
  void getListById_withNonExistentId_returns404RegardlessOfOwner() throws Exception {
    mockMvc
        .perform(get("/lists/" + UUID.randomUUID()).with(TestAuth.user2()))
        .andExpect(status().isNotFound())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("LIST_NOT_FOUND"));
  }

  /** Devuelve 404 antes que 403 al añadir un ítem a una lista inexistente. */
  @Test
  void addItem_withNonExistentList_returns404RegardlessOfOwner() throws Exception {
    mockMvc
        .perform(
            post("/lists/" + UUID.randomUUID() + "/items")
                .with(TestAuth.user2())
                .contentType(MediaType.APPLICATION_JSON)
                .content(addItemBody()))
        .andExpect(status().isNotFound())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("LIST_NOT_FOUND"));
  }

  /** Rechaza con 403 al añadir un ítem a una lista ajena antes de consultar el catálogo. */
  @Test
  void addItem_withOtherOwner_returns403OwnerMismatch() throws Exception {
    ShoppingList list = buildList(OWNER_ID, "Compra semanal");
    persist(list);

    mockMvc
        .perform(
            post("/lists/" + list.getPublicId() + "/items")
                .with(TestAuth.user2())
                .contentType(MediaType.APPLICATION_JSON)
                .content(addItemBody()))
        .andExpect(status().isForbidden())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("OWNER_MISMATCH"));
  }

  /** Devuelve 404 antes que 403 al modificar un ítem de una lista inexistente. */
  @Test
  void updateItemPurchased_withNonExistentList_returns404RegardlessOfOwner() throws Exception {
    mockMvc
        .perform(
            patch("/lists/" + UUID.randomUUID() + "/items/" + UUID.randomUUID())
                .with(TestAuth.user2())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"purchased\": true}"))
        .andExpect(status().isNotFound())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("LIST_NOT_FOUND"));
  }

  /** Rechaza con 403 al modificar un ítem de una lista ajena. */
  @Test
  void updateItemPurchased_withOtherOwner_returns403OwnerMismatch() throws Exception {
    ShoppingList list = buildList(OWNER_ID, "Compra semanal");
    persist(list);
    ListItem item = buildItem(list.getId(), UUID.randomUUID(), ProductType.BASE, "Leche", false);
    persist(item);

    mockMvc
        .perform(
            patch("/lists/" + list.getPublicId() + "/items/" + item.getPublicId())
                .with(TestAuth.user2())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"purchased\": true}"))
        .andExpect(status().isForbidden())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("OWNER_MISMATCH"));
  }

  /** Devuelve 404 cuando el ítem a modificar no existe en una lista propia. */
  @Test
  void updateItemPurchased_withNonExistentItem_returns404ListItemNotFound() throws Exception {
    ShoppingList list = buildList(OWNER_ID, "Compra semanal");
    persist(list);

    mockMvc
        .perform(
            patch("/lists/" + list.getPublicId() + "/items/" + UUID.randomUUID())
                .with(TestAuth.user1())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"purchased\": true}"))
        .andExpect(status().isNotFound())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("LIST_ITEM_NOT_FOUND"));
  }

  /** Devuelve 404 antes que 403 al eliminar un ítem de una lista inexistente. */
  @Test
  void removeItem_withNonExistentList_returns404RegardlessOfOwner() throws Exception {
    mockMvc
        .perform(
            delete("/lists/" + UUID.randomUUID() + "/items/" + UUID.randomUUID())
                .with(TestAuth.user2()))
        .andExpect(status().isNotFound())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("LIST_NOT_FOUND"));
  }

  /** Rechaza con 403 al eliminar un ítem de una lista ajena. */
  @Test
  void removeItem_withOtherOwner_returns403OwnerMismatch() throws Exception {
    ShoppingList list = buildList(OWNER_ID, "Compra semanal");
    persist(list);
    ListItem item = buildItem(list.getId(), UUID.randomUUID(), ProductType.BASE, "Leche", false);
    persist(item);

    mockMvc
        .perform(
            delete("/lists/" + list.getPublicId() + "/items/" + item.getPublicId())
                .with(TestAuth.user2()))
        .andExpect(status().isForbidden())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("OWNER_MISMATCH"));
  }

  /** Devuelve 404 cuando el ítem a eliminar no existe en una lista propia. */
  @Test
  void removeItem_withNonExistentItem_returns404ListItemNotFound() throws Exception {
    ShoppingList list = buildList(OWNER_ID, "Compra semanal");
    persist(list);

    mockMvc
        .perform(
            delete("/lists/" + list.getPublicId() + "/items/" + UUID.randomUUID())
                .with(TestAuth.user1()))
        .andExpect(status().isNotFound())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("LIST_ITEM_NOT_FOUND"));
  }

  private String addItemBody() {
    return """
        {
          "productId": "%s",
          "productType": "BASE"
        }
        """
        .formatted(UUID.randomUUID());
  }

  /**
   * Persiste la lista indicada dentro de la transacción del test y fuerza el {@code flush} para que
   * JPA asigne los identificadores generados.
   *
   * @param list lista a insertar
   */
  private void persist(ShoppingList list) {
    entityManager.persist(list);
    entityManager.flush();
  }

  /**
   * Persiste el ítem indicado dentro de la transacción del test y fuerza el {@code flush} para que
   * JPA asigne los identificadores generados.
   *
   * @param item ítem a insertar
   */
  private void persist(ListItem item) {
    entityManager.persist(item);
    entityManager.flush();
  }

  /**
   * Construye una lista sin persistir para el propietario indicado.
   *
   * @param ownerId propietario de la lista
   * @param name nombre de la lista
   * @return entidad {@link ShoppingList} sin persistir
   */
  private ShoppingList buildList(UUID ownerId, String name) {
    ShoppingList list = new ShoppingList();
    list.setOwnerId(ownerId);
    list.setName(name);
    return list;
  }

  /**
   * Construye un ítem sin persistir asociado a la lista indicada.
   *
   * @param listId id interno de la lista propietaria
   * @param productId identificador público del producto referenciado
   * @param productType tipo de producto referenciado
   * @param displayName snapshot del nombre del producto
   * @param purchased si el ítem está comprado
   * @return entidad {@link ListItem} sin persistir
   */
  private ListItem buildItem(
      Long listId, UUID productId, ProductType productType, String displayName, boolean purchased) {
    ListItem item = new ListItem();
    item.setListId(listId);
    item.setProductId(productId);
    item.setProductType(productType);
    item.setDisplayName(displayName);
    item.setPurchased(purchased);
    return item;
  }
}
