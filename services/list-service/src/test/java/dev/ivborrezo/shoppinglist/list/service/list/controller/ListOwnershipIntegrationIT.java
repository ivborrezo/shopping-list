package dev.ivborrezo.shoppinglist.list.service.list.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.ivborrezo.shoppinglist.list.service.common.ProductType;
import dev.ivborrezo.shoppinglist.list.service.list.dto.ShoppingListResponse;
import dev.ivborrezo.shoppinglist.list.service.list.entity.ListItem;
import dev.ivborrezo.shoppinglist.list.service.list.entity.ShoppingList;
import dev.ivborrezo.shoppinglist.list.service.list.repository.ListItemRepository;
import dev.ivborrezo.shoppinglist.list.service.list.repository.ShoppingListRepository;
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
 * Test de integración de la verificación de propiedad sobre las mutaciones de {@code /lists}.
 *
 * <p>Ejercita vía MockMvc el contrato de {@code api-contract.yaml}: {@code PATCH} y {@code DELETE}
 * solo proceden cuando el {@code ownerId} solicitante coincide con el propietario almacenado y
 * responden {@code 403 OWNER_MISMATCH} en caso contrario; el recurso inexistente prevalece como
 * {@code 404 LIST_NOT_FOUND}. La lectura por identificador se mantiene abierta.
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

  private static final UUID OWNER_ID = UUID.fromString("aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee");

  private static final UUID OTHER_OWNER_ID =
      UUID.fromString("aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeef");

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

  /** Rechaza con 403 y {@code OWNER_MISMATCH} el renombrado por un propietario distinto. */
  @Test
  void patchList_withOtherOwner_returns403AndKeepsName() throws Exception {
    ShoppingList list = buildList(OWNER_ID, "Nombre antiguo");
    persist(list);

    String body =
        """
        {
          "ownerId": "%s",
          "name": "Nombre nuevo"
        }
        """
            .formatted(OTHER_OWNER_ID);

    mockMvc
        .perform(
            patch("/lists/" + list.getPublicId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isForbidden())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("OWNER_MISMATCH"));

    entityManager.refresh(list);
    assertThat(list.getName()).isEqualTo("Nombre antiguo");
  }

  /** Permite el renombrado cuando el {@code ownerId} coincide con el propietario almacenado. */
  @Test
  void patchList_withMatchingOwner_returns200AndNewName() throws Exception {
    ShoppingList list = buildList(OWNER_ID, "Nombre antiguo");
    persist(list);

    String body =
        """
        {
          "ownerId": "%s",
          "name": "Nombre nuevo"
        }
        """
            .formatted(OWNER_ID);

    MvcResult result =
        mockMvc
            .perform(
                patch("/lists/" + list.getPublicId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isOk())
            .andReturn();

    ShoppingListResponse updated =
        objectMapper.readValue(
            result.getResponse().getContentAsByteArray(), ShoppingListResponse.class);

    assertThat(updated.name()).isEqualTo("Nombre nuevo");
  }

  /** Devuelve 404 antes que 403 cuando la lista a renombrar no existe, sea cual sea el owner. */
  @Test
  void patchList_withNonExistentId_returns404RegardlessOfOwner() throws Exception {
    String body =
        """
        {
          "ownerId": "%s",
          "name": "Nombre nuevo"
        }
        """
            .formatted(OTHER_OWNER_ID);

    mockMvc
        .perform(
            patch("/lists/" + UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isNotFound())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("LIST_NOT_FOUND"));
  }

  /** Rechaza con 403 el borrado por un propietario distinto y conserva la lista. */
  @Test
  void deleteList_withOtherOwner_returns403AndKeepsList() throws Exception {
    ShoppingList list = buildList(OWNER_ID, "Ajena");
    persist(list);

    mockMvc
        .perform(delete("/lists/" + list.getPublicId()).param("ownerId", OTHER_OWNER_ID.toString()))
        .andExpect(status().isForbidden())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("OWNER_MISMATCH"));

    assertThat(shoppingListRepository.findByPublicId(list.getPublicId())).isPresent();
  }

  /** Elimina la lista y sus ítems con 204 cuando el {@code ownerId} coincide. */
  @Test
  void deleteList_withMatchingOwner_returns204AndRemovesList() throws Exception {
    ShoppingList list = buildList(OWNER_ID, "Para borrar");
    persist(list);
    ListItem item = buildItem(list.getId(), UUID.randomUUID(), ProductType.BASE, "Leche", false);
    persist(item);

    mockMvc
        .perform(delete("/lists/" + list.getPublicId()).param("ownerId", OWNER_ID.toString()))
        .andExpect(status().isNoContent());

    assertThat(shoppingListRepository.findByPublicId(list.getPublicId())).isEmpty();
    assertThat(listItemRepository.findByPublicId(item.getPublicId())).isEmpty();
  }

  /** Devuelve 400 con ProblemDetail cuando falta el query param {@code ownerId} al borrar. */
  @Test
  void deleteList_withoutOwnerId_returnsValidationProblemDetail() throws Exception {
    ShoppingList list = buildList(OWNER_ID, "Sin owner");
    persist(list);

    mockMvc
        .perform(delete("/lists/" + list.getPublicId()))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  /** Devuelve 400 con ProblemDetail cuando {@code ownerId} no es un UUID válido al borrar. */
  @Test
  void deleteList_withMalformedOwnerId_returnsValidationProblemDetail() throws Exception {
    ShoppingList list = buildList(OWNER_ID, "Owner inválido");
    persist(list);

    mockMvc
        .perform(delete("/lists/" + list.getPublicId()).param("ownerId", "not-a-uuid"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  /** Devuelve 404 antes que 403 cuando la lista a eliminar no existe, sea cual sea el owner. */
  @Test
  void deleteList_withNonExistentId_returns404RegardlessOfOwner() throws Exception {
    mockMvc
        .perform(delete("/lists/" + UUID.randomUUID()).param("ownerId", OTHER_OWNER_ID.toString()))
        .andExpect(status().isNotFound())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("LIST_NOT_FOUND"));
  }

  /** Mantiene la lectura por identificador abierta, sin exigir {@code ownerId}. */
  @Test
  void getListById_withoutOwner_returns200() throws Exception {
    ShoppingList list = buildList(OWNER_ID, "Lectura abierta");
    persist(list);

    mockMvc.perform(get("/lists/" + list.getPublicId())).andExpect(status().isOk());
  }

  /**
   * Devuelve el detalle de una lista de otro propietario al leerla sin {@code ownerId}, demostrando
   * que la lectura es abierta entre propietarios.
   */
  @Test
  void getListById_withoutOwner_returnsListOfAnyOwner() throws Exception {
    ShoppingList list = buildList(OTHER_OWNER_ID, "Lista de otro propietario");
    persist(list);

    MvcResult result =
        mockMvc.perform(get("/lists/" + list.getPublicId())).andExpect(status().isOk()).andReturn();

    ShoppingListResponse response =
        objectMapper.readValue(
            result.getResponse().getContentAsByteArray(), ShoppingListResponse.class);

    assertThat(response.id()).isEqualTo(list.getPublicId());
    assertThat(response.ownerId()).isEqualTo(OTHER_OWNER_ID);
    assertThat(response.name()).isEqualTo("Lista de otro propietario");
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
