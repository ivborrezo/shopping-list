package dev.ivborrezo.shoppinglist.list.service.list.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.ivborrezo.shoppinglist.list.service.common.ProductType;
import dev.ivborrezo.shoppinglist.list.service.list.dto.ListItemResponse;
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
 * Test de integración de los endpoints REST de {@code /lists}.
 *
 * <p>Arranca el contexto completo contra PostgreSQL real (Testcontainers) y ejercita vía MockMvc el
 * contrato de {@code api-contract.yaml}: creación con {@code Location} y propietario derivado del
 * JWT, listado paginado del usuario autenticado sin ítems, detalle con ítems ordenados, renombrado
 * y borrado, además de los errores de validación ({@code 400}) y recurso inexistente ({@code 404}).
 */
@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@AutoConfigureMockMvc
@AutoConfigureTestEntityManager
@TestConstructor(autowireMode = AutowireMode.ALL)
@Transactional
@Testcontainers
class ListControllerIntegrationIT {

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
  ListControllerIntegrationIT(
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
   * Crea una lista con body válido, asigna el propietario del token y devuelve 201 con la cabecera
   * {@code Location}.
   */
  @Test
  void createList_withValidBody_returns201AndLocation() throws Exception {
    String body =
        """
        {
          "name": "Compra semanal"
        }
        """;

    MvcResult result =
        mockMvc
            .perform(
                post("/lists")
                    .with(TestAuth.user1())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isCreated())
            .andExpect(header().exists("Location"))
            .andReturn();

    ShoppingListResponse created =
        objectMapper.readValue(
            result.getResponse().getContentAsByteArray(), ShoppingListResponse.class);

    assertThat(created.id()).isNotNull();
    assertThat(created.ownerId()).isEqualTo(OWNER_ID);
    assertThat(created.name()).isEqualTo("Compra semanal");
    assertThat(created.items()).isEmpty();
    assertThat(result.getResponse().getHeader("Location")).isEqualTo("/lists/" + created.id());
  }

  /** Rechaza con 400 y ProblemDetail la creación de una lista con nombre en blanco. */
  @Test
  void createList_withBlankName_returnsValidationProblemDetail() throws Exception {
    String body =
        """
        {
          "name": ""
        }
        """;

    mockMvc
        .perform(
            post("/lists")
                .with(TestAuth.user1())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  /** Rechaza con 400 y ProblemDetail la creación de una lista sin nombre. */
  @Test
  void createList_withoutName_returnsValidationProblemDetail() throws Exception {
    String body = "{}";

    mockMvc
        .perform(
            post("/lists")
                .with(TestAuth.user1())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  /** Rechaza con 400 y ProblemDetail la creación de una lista con un nombre demasiado largo. */
  @Test
  void createList_withNameTooLong_returnsValidationProblemDetail() throws Exception {
    String body =
        """
        {
          "name": "%s"
        }
        """
            .formatted("a".repeat(129));

    mockMvc
        .perform(
            post("/lists")
                .with(TestAuth.user1())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.errors[0].field").value("name"));
  }

  /** Rechaza con 400 y ProblemDetail la creación de una lista con un body JSON malformado. */
  @Test
  void postList_withMalformedBody_returnsValidationProblemDetail() throws Exception {
    mockMvc
        .perform(
            post("/lists")
                .with(TestAuth.user1())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ not json"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  /**
   * Lista solo las listas del usuario autenticado con el shape de página del contrato y resúmenes
   * sin ítems, aislando las listas de otros usuarios.
   */
  @Test
  void listLists_byAuthenticatedUser_returnsOnlyOwnSummariesWithoutItems() throws Exception {
    persist(buildList(OWNER_ID, "Compra uno"));
    persist(buildList(OWNER_ID, "Compra dos"));
    persist(buildList(OTHER_OWNER_ID, "Ajena"));

    mockMvc
        .perform(get("/lists").with(TestAuth.user1()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.page").value(0))
        .andExpect(jsonPath("$.size").value(20))
        .andExpect(jsonPath("$.totalElements").value(2))
        .andExpect(jsonPath("$.content.length()").value(2))
        .andExpect(jsonPath("$.content[0].ownerId").value(OWNER_ID.toString()))
        .andExpect(jsonPath("$.content[1].ownerId").value(OWNER_ID.toString()))
        .andExpect(jsonPath("$.content[0].items").doesNotExist());
  }

  /** Ordena el listado por actividad descendente (la lista modificada más tarde primero). */
  @Test
  void listLists_byAuthenticatedUser_ordersByUpdatedAtDesc() throws Exception {
    ShoppingList older = buildList(OWNER_ID, "Lista antigua");
    persist(older);
    Thread.sleep(10);
    ShoppingList newer = buildList(OWNER_ID, "Lista reciente");
    persist(newer);

    mockMvc
        .perform(get("/lists").with(TestAuth.user1()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].id").value(newer.getPublicId().toString()))
        .andExpect(jsonPath("$.content[1].id").value(older.getPublicId().toString()));
  }

  /** Devuelve una página vacía cuando el usuario autenticado no tiene ninguna lista. */
  @Test
  void listLists_withoutLists_returnsEmptyPage() throws Exception {
    mockMvc
        .perform(get("/lists").with(TestAuth.user1()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(0))
        .andExpect(jsonPath("$.content").isEmpty());
  }

  /** Rechaza con 401 UNAUTHENTICATED el listado sin credenciales. */
  @Test
  void listLists_withoutCredentials_returns401Unauthenticated() throws Exception {
    mockMvc
        .perform(get("/lists"))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
  }

  /** Recupera el detalle de una lista propia con sus ítems ordenados por estado y orden de alta. */
  @Test
  void getListById_withItems_returnsItemsInlineInOrder() throws Exception {
    ShoppingList list = buildList(OWNER_ID, "Compra con items");
    persist(list);

    ListItem purchased =
        buildItem(list.getId(), UUID.randomUUID(), ProductType.BASE, "Leche", true);
    ListItem pending = buildItem(list.getId(), UUID.randomUUID(), ProductType.USER, "Pan", false);
    persist(purchased, pending);

    MvcResult result =
        mockMvc
            .perform(get("/lists/" + list.getPublicId()).with(TestAuth.user1()))
            .andExpect(status().isOk())
            .andReturn();

    ShoppingListResponse response =
        objectMapper.readValue(
            result.getResponse().getContentAsByteArray(), ShoppingListResponse.class);

    assertThat(response.items()).hasSize(2);

    ListItemResponse first = response.items().get(0);
    assertThat(first.id()).isEqualTo(pending.getPublicId());
    assertThat(first.productId()).isEqualTo(pending.getProductId());
    assertThat(first.productType()).isEqualTo(ProductType.USER);
    assertThat(first.displayName()).isEqualTo("Pan");
    assertThat(first.purchased()).isFalse();

    ListItemResponse second = response.items().get(1);
    assertThat(second.id()).isEqualTo(purchased.getPublicId());
    assertThat(second.productType()).isEqualTo(ProductType.BASE);
    assertThat(second.displayName()).isEqualTo("Leche");
    assertThat(second.purchased()).isTrue();
  }

  /** Devuelve 404 con ProblemDetail cuando la lista solicitada no existe. */
  @Test
  void getListById_withNonExistentId_returnsListNotFound() throws Exception {
    mockMvc
        .perform(get("/lists/" + UUID.randomUUID()).with(TestAuth.user1()))
        .andExpect(status().isNotFound())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("LIST_NOT_FOUND"));
  }

  /** Devuelve 400 con ProblemDetail cuando el identificador de lista no es un UUID válido. */
  @Test
  void getListById_withMalformedId_returnsValidationProblemDetail() throws Exception {
    mockMvc
        .perform(get("/lists/not-a-uuid").with(TestAuth.user1()))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  /** Renombra una lista propia y devuelve el nombre actualizado. */
  @Test
  void patchList_withNewName_returnsUpdatedList() throws Exception {
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

  /** Un body vacío en el renombrado conserva el nombre actual y devuelve 200. */
  @Test
  void patchList_withEmptyBody_keepsExistingName() throws Exception {
    ShoppingList list = buildList(OWNER_ID, "Nombre original");
    persist(list);

    MvcResult result =
        mockMvc
            .perform(
                patch("/lists/" + list.getPublicId())
                    .with(TestAuth.user1())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
            .andExpect(status().isOk())
            .andReturn();

    ShoppingListResponse updated =
        objectMapper.readValue(
            result.getResponse().getContentAsByteArray(), ShoppingListResponse.class);

    assertThat(updated.name()).isEqualTo("Nombre original");
  }

  /** Rechaza con 400 y ProblemDetail el renombrado con un nombre demasiado largo. */
  @Test
  void patchList_withNameTooLong_returnsValidationProblemDetail() throws Exception {
    ShoppingList list = buildList(OWNER_ID, "Nombre antiguo");
    persist(list);

    String body =
        """
        {
          "name": "%s"
        }
        """
            .formatted("a".repeat(129));

    mockMvc
        .perform(
            patch("/lists/" + list.getPublicId())
                .with(TestAuth.user1())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.errors[0].field").value("name"));
  }

  /** Devuelve 404 cuando la lista a renombrar no existe. */
  @Test
  void patchList_withNonExistentId_returns404() throws Exception {
    String body =
        """
        {
          "name": "Nombre nuevo"
        }
        """;

    mockMvc
        .perform(
            patch("/lists/" + UUID.randomUUID())
                .with(TestAuth.user1())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isNotFound());
  }

  /** Elimina una lista propia y devuelve 204, dejando de existir ella y sus ítems. */
  @Test
  void deleteList_withOwner_returns204AndRemovesList() throws Exception {
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

  /** Devuelve 404 cuando la lista a eliminar no existe. */
  @Test
  void deleteList_withNonExistentId_returns404() throws Exception {
    mockMvc
        .perform(delete("/lists/" + UUID.randomUUID()).with(TestAuth.user1()))
        .andExpect(status().isNotFound());
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
   * Persiste los ítems indicados dentro de la transacción del test y fuerza el {@code flush} para
   * que JPA asigne los identificadores generados.
   *
   * @param items ítems a insertar
   */
  private void persist(ListItem... items) {
    for (ListItem item : items) {
      entityManager.persist(item);
    }
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
