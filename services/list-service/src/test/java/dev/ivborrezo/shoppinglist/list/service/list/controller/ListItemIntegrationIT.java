package dev.ivborrezo.shoppinglist.list.service.list.controller;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.notFound;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.github.tomakehurst.wiremock.WireMockServer;
import dev.ivborrezo.shoppinglist.list.service.common.ProductType;
import dev.ivborrezo.shoppinglist.list.service.common.event.LoggingDomainEventPublisher;
import dev.ivborrezo.shoppinglist.list.service.list.entity.ListItem;
import dev.ivborrezo.shoppinglist.list.service.list.entity.ShoppingList;
import dev.ivborrezo.shoppinglist.list.service.list.repository.ListItemRepository;
import dev.ivborrezo.shoppinglist.list.service.list.repository.ShoppingListRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.jpa.test.autoconfigure.AutoConfigureTestEntityManager;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.TestConstructor.AutowireMode;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Test de integración de la resolución de productos contra un {@code product-service} simulado.
 *
 * <p>Arranca el contexto completo contra PostgreSQL real (Testcontainers) y un servidor WireMock en
 * proceso como stub de {@code product-service}, sin sustituir {@code ProductCatalogClient}: el
 * flujo real de resolución de nombres, la propagación de cabeceras y la traducción de errores se
 * ejercitan de extremo a extremo. Comprueba además que las mutaciones de ítems publican su evento
 * de dominio solo tras confirmar la transacción.
 */
@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@AutoConfigureMockMvc
@AutoConfigureTestEntityManager
@TestConstructor(autowireMode = AutowireMode.ALL)
@Transactional
@Testcontainers
class ListItemIntegrationIT {

  @Container @ServiceConnection
  static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

  static final WireMockServer WIRE_MOCK = new WireMockServer(options().dynamicPort());

  private static final UUID OWNER_ID = UUID.fromString("aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee");

  private static final String CORRELATION = "corr-it";

  private static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

  private final MockMvc mockMvc;

  private final TestEntityManager entityManager;

  private final ShoppingListRepository shoppingListRepository;

  private final ListItemRepository listItemRepository;

  private Logger publisherLogger;

  private ListAppender<ILoggingEvent> appender;

  /**
   * Inyecta las dependencias de test por constructor, sin {@code @Autowired} por campo, coherente
   * con la convención del resto del monorepo. El catálogo de productos no se inyecta: se usa el
   * adaptador real contra WireMock.
   *
   * @param mockMvc cliente MockMvc contra el DispatcherServlet real
   * @param entityManager gestor JPA para limpiar el contexto de persistencia tras sembrar
   * @param shoppingListRepository repositorio de listas para sembrar los datos de cada escenario
   * @param listItemRepository repositorio de ítems para sembrar los datos de soporte
   */
  ListItemIntegrationIT(
      MockMvc mockMvc,
      TestEntityManager entityManager,
      ShoppingListRepository shoppingListRepository,
      ListItemRepository listItemRepository) {
    this.mockMvc = mockMvc;
    this.entityManager = entityManager;
    this.shoppingListRepository = shoppingListRepository;
    this.listItemRepository = listItemRepository;
  }

  /**
   * Enlaza el servidor WireMock al puerto dinámico elegido y fija el timeout de lectura corto del
   * escenario de timeout.
   *
   * @param registry registro de propiedades dinámicas del contexto de test
   */
  @DynamicPropertySource
  static void wireMockProperties(DynamicPropertyRegistry registry) {
    WIRE_MOCK.start();
    registry.add("product-service.client.base-url", () -> WIRE_MOCK.baseUrl());
    registry.add("product-service.client.read-timeout", () -> "500ms");
  }

  /** Detiene el servidor WireMock al terminar la clase. */
  @AfterAll
  static void stopWireMock() {
    WIRE_MOCK.stop();
  }

  /** Reinicia los stubs y las peticiones registradas antes de cada test. */
  @BeforeEach
  void reset() {
    WIRE_MOCK.resetAll();
  }

  /**
   * Engancha un {@link ListAppender} al logger del adaptador de publicación para capturar los
   * eventos entregados durante el test.
   */
  @BeforeEach
  void setUp() {
    publisherLogger = (Logger) LoggerFactory.getLogger(LoggingDomainEventPublisher.class);
    appender = new ListAppender<>();
    appender.start();
    publisherLogger.addAppender(appender);
  }

  /** Desengancha el appender del logger del adaptador al terminar el test. */
  @AfterEach
  void tearDown() {
    publisherLogger.detachAppender(appender);
    appender.stop();
  }

  /**
   * Añade un producto base resolviendo su nombre contra WireMock, propaga solo las cabeceras de la
   * lista blanca y publica el evento {@code list.item.added} tras el commit.
   *
   * @throws Exception si la petición HTTP simulada falla
   */
  @Test
  void addBaseItem_withValidProduct_publishesEventAfterCommitAndPropagatesWhitelist()
      throws Exception {
    ShoppingList list = seedList(OWNER_ID, "Compra semanal");
    UUID productId = UUID.randomUUID();
    WIRE_MOCK.stubFor(
        get(urlEqualTo("/base-products/" + productId))
            .willReturn(okJson("{\"name\":\"Leche entera\"}")));

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
                    .header(CORRELATION_ID_HEADER, CORRELATION)
                    .header("Accept-Language", "es-ES,es;q=0.9")
                    .header("X-Custom-Header", "nope")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isCreated())
            .andExpect(header().exists("Location"))
            .andExpect(jsonPath("$.displayName").value("Leche entera"))
            .andExpect(jsonPath("$.purchased").value(false))
            .andReturn();

    String itemId =
        result
            .getResponse()
            .getHeader("Location")
            .substring(("/lists/" + list.getPublicId() + "/items/").length());

    WIRE_MOCK.verify(
        getRequestedFor(urlEqualTo("/base-products/" + productId))
            .withHeader(CORRELATION_ID_HEADER, equalTo(CORRELATION))
            .withHeader("Accept-Language", equalTo("es-ES,es;q=0.9"))
            .withoutHeader("X-Custom-Header"));

    assertThat(eventsFor(CORRELATION)).isEmpty();

    TestTransaction.flagForCommit();
    TestTransaction.end();

    List<ILoggingEvent> events = eventsFor(CORRELATION);
    assertThat(events).hasSize(1);
    assertThat(events.get(0).getFormattedMessage())
        .contains(
            "\"eventType\":\"list.item.added\"",
            "\"correlationId\":\"" + CORRELATION + "\"",
            "\"listId\":\"" + list.getPublicId() + "\"",
            "\"itemId\":\"" + itemId + "\"",
            "\"productId\":\"" + productId + "\"",
            "\"productType\":\"BASE\"",
            "\"displayName\":\"Leche entera\"");
  }

  /**
   * Añade un producto de usuario resolviendo su nombre por el path de productos de usuario.
   *
   * @throws Exception si la petición HTTP simulada falla
   */
  @Test
  void addUserItem_withValidProduct_resolvesFromUserProductPath() throws Exception {
    ShoppingList list = seedList(OWNER_ID, "Compra semanal");
    UUID productId = UUID.randomUUID();
    WIRE_MOCK.stubFor(
        get(urlEqualTo("/user-products/" + productId)).willReturn(okJson("{\"name\":\"Pan\"}")));

    String body =
        """
        {
          "productId": "%s",
          "productType": "USER"
        }
        """
            .formatted(productId);

    mockMvc
        .perform(
            post("/lists/" + list.getPublicId() + "/items")
                .param("ownerId", OWNER_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.displayName").value("Pan"))
        .andExpect(jsonPath("$.purchased").value(false));

    WIRE_MOCK.verify(getRequestedFor(urlEqualTo("/user-products/" + productId)));
  }

  /**
   * Traduce el 404 del catálogo a un 400 con el código {@code INVALID_PRODUCT_REFERENCE}.
   *
   * @throws Exception si la petición HTTP simulada falla
   */
  @Test
  void addItem_whenProductNotFound_returnsInvalidProductReference() throws Exception {
    ShoppingList list = seedList(OWNER_ID, "Compra semanal");
    UUID productId = UUID.randomUUID();
    WIRE_MOCK.stubFor(get(urlEqualTo("/base-products/" + productId)).willReturn(notFound()));

    mockMvc
        .perform(
            post("/lists/" + list.getPublicId() + "/items")
                .param("ownerId", OWNER_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(addItemBody(productId, "BASE")))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("INVALID_PRODUCT_REFERENCE"));
  }

  /**
   * Traduce un 5xx del catálogo a un 503 con el código {@code PRODUCT_SERVICE_UNAVAILABLE}.
   *
   * @throws Exception si la petición HTTP simulada falla
   */
  @Test
  void addItem_whenProductServiceFails_returnsProductServiceUnavailable() throws Exception {
    ShoppingList list = seedList(OWNER_ID, "Compra semanal");
    UUID productId = UUID.randomUUID();
    WIRE_MOCK.stubFor(get(urlEqualTo("/base-products/" + productId)).willReturn(serverError()));

    mockMvc
        .perform(
            post("/lists/" + list.getPublicId() + "/items")
                .param("ownerId", OWNER_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(addItemBody(productId, "BASE")))
        .andExpect(status().isServiceUnavailable())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("PRODUCT_SERVICE_UNAVAILABLE"));
  }

  /**
   * Traduce el exceso del timeout de lectura del catálogo a un 503 con el código {@code
   * PRODUCT_SERVICE_UNAVAILABLE}.
   *
   * @throws Exception si la petición HTTP simulada falla
   */
  @Test
  void addItem_whenProductServiceTimesOut_returnsProductServiceUnavailable() throws Exception {
    ShoppingList list = seedList(OWNER_ID, "Compra semanal");
    UUID productId = UUID.randomUUID();
    WIRE_MOCK.stubFor(
        get(urlEqualTo("/base-products/" + productId))
            .willReturn(okJson("{\"name\":\"X\"}").withFixedDelay(1500)));

    mockMvc
        .perform(
            post("/lists/" + list.getPublicId() + "/items")
                .param("ownerId", OWNER_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(addItemBody(productId, "BASE")))
        .andExpect(status().isServiceUnavailable())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("PRODUCT_SERVICE_UNAVAILABLE"));
  }

  /**
   * Marca un ítem como comprado y publica el evento {@code list.item.purchased} tras el commit, con
   * el nuevo estado y los identificadores públicos.
   *
   * @throws Exception si la petición HTTP simulada falla
   */
  @Test
  void updateItemPurchased_publishesPurchasedEventAfterCommit() throws Exception {
    ShoppingList list = seedList(OWNER_ID, "Compra semanal");
    UUID productId = UUID.randomUUID();
    ListItem item = seedItem(list.getId(), productId, ProductType.BASE, "Leche", false);

    String body =
        """
        {
          "purchased": true
        }
        """;

    mockMvc
        .perform(
            patch("/lists/" + list.getPublicId() + "/items/" + item.getPublicId())
                .param("ownerId", OWNER_ID.toString())
                .header(CORRELATION_ID_HEADER, CORRELATION)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.purchased").value(true));

    assertThat(eventsFor(CORRELATION)).isEmpty();

    TestTransaction.flagForCommit();
    TestTransaction.end();

    List<ILoggingEvent> events = eventsFor(CORRELATION);
    assertThat(events).hasSize(1);
    assertThat(events.get(0).getFormattedMessage())
        .contains(
            "\"eventType\":\"list.item.purchased\"",
            "\"correlationId\":\"" + CORRELATION + "\"",
            "\"listId\":\"" + list.getPublicId() + "\"",
            "\"itemId\":\"" + item.getPublicId() + "\"",
            "\"productId\":\"" + productId + "\"",
            "\"productType\":\"BASE\"",
            "\"displayName\":\"Leche\"",
            "\"purchased\":true");
  }

  /**
   * Elimina un ítem y publica el evento {@code list.item.removed} tras el commit, con los
   * identificadores públicos.
   *
   * @throws Exception si la petición HTTP simulada falla
   */
  @Test
  void removeItem_publishesRemovedEventAfterCommit() throws Exception {
    ShoppingList list = seedList(OWNER_ID, "Compra semanal");
    UUID productId = UUID.randomUUID();
    ListItem item = seedItem(list.getId(), productId, ProductType.BASE, "Leche", false);

    mockMvc
        .perform(
            delete("/lists/" + list.getPublicId() + "/items/" + item.getPublicId())
                .param("ownerId", OWNER_ID.toString())
                .header(CORRELATION_ID_HEADER, CORRELATION))
        .andExpect(status().isNoContent());

    assertThat(eventsFor(CORRELATION)).isEmpty();

    TestTransaction.flagForCommit();
    TestTransaction.end();

    List<ILoggingEvent> events = eventsFor(CORRELATION);
    assertThat(events).hasSize(1);
    assertThat(events.get(0).getFormattedMessage())
        .contains(
            "\"eventType\":\"list.item.removed\"",
            "\"correlationId\":\"" + CORRELATION + "\"",
            "\"listId\":\"" + list.getPublicId() + "\"",
            "\"itemId\":\"" + item.getPublicId() + "\"",
            "\"productId\":\"" + productId + "\"",
            "\"productType\":\"BASE\"",
            "\"displayName\":\"Leche\"");
  }

  /**
   * Construye el body JSON de alta de ítem para las pruebas que no necesitan inspeccionarlo.
   *
   * @param productId identificador del producto referenciado
   * @param productType tipo de producto referenciado
   * @return body JSON de la petición de alta
   */
  private String addItemBody(UUID productId, String productType) {
    return """
        {
          "productId": "%s",
          "productType": "%s"
        }
        """
        .formatted(productId, productType);
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

  /**
   * Filtra los eventos capturados por el {@code correlationId} de la petición.
   *
   * @param correlationId identificador de correlación del test
   * @return eventos cuyo envelope lleva ese {@code correlationId}
   */
  private List<ILoggingEvent> eventsFor(String correlationId) {
    return appender.list.stream()
        .filter(
            event ->
                event.getFormattedMessage().contains("\"correlationId\":\"" + correlationId + "\""))
        .toList();
  }
}
