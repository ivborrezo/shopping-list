package dev.ivborrezo.shoppinglist.list.service.list.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import dev.ivborrezo.shoppinglist.list.service.common.event.LoggingDomainEventPublisher;
import dev.ivborrezo.shoppinglist.list.service.list.entity.ShoppingList;
import dev.ivborrezo.shoppinglist.list.service.list.repository.ShoppingListRepository;
import java.util.List;
import java.util.UUID;
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
 * Test de integración de la publicación de eventos de dominio de listas.
 *
 * <p>Arranca el contexto completo contra PostgreSQL real (Testcontainers) y ejercita los endpoints
 * de creación, renombrado y borrado vía MockMvc. Captura el log del adaptador real {@link
 * LoggingDomainEventPublisher} para comprobar que el evento solo se entrega tras confirmar la
 * transacción ({@code after-commit}) y que el envelope lleva el {@code correlationId} propagado por
 * la cabecera {@code X-Correlation-Id}.
 */
@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@AutoConfigureMockMvc
@AutoConfigureTestEntityManager
@TestConstructor(autowireMode = AutowireMode.ALL)
@Transactional
@Testcontainers
class ListEventPublicationIntegrationIT {

  @Container @ServiceConnection
  static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

  private static final UUID OWNER_ID = UUID.fromString("aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee");

  private static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

  private final MockMvc mockMvc;

  private final TestEntityManager entityManager;

  private final ShoppingListRepository shoppingListRepository;

  private Logger publisherLogger;

  private ListAppender<ILoggingEvent> appender;

  /**
   * Inyecta las dependencias de test por constructor, sin {@code @Autowired} por campo, coherente
   * con la convención del resto del monorepo.
   *
   * @param mockMvc cliente MockMvc contra el DispatcherServlet real
   * @param entityManager gestor JPA para limpiar el contexto de persistencia tras sembrar
   * @param shoppingListRepository repositorio de listas para sembrar los datos de cada escenario
   */
  ListEventPublicationIntegrationIT(
      MockMvc mockMvc,
      TestEntityManager entityManager,
      ShoppingListRepository shoppingListRepository) {
    this.mockMvc = mockMvc;
    this.entityManager = entityManager;
    this.shoppingListRepository = shoppingListRepository;
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
   * Comprueba que un alta con cabecera de correlación no entrega el evento antes del commit y lo
   * entrega una sola vez después, con el tipo y el payload esperados.
   *
   * @throws Exception si la petición HTTP simulada falla
   */
  @Test
  void createList_withCorrelationHeader_publishesEventOnlyAfterCommit() throws Exception {
    String correlationId = "corr-created";
    String body =
        """
        {
          "ownerId": "%s",
          "name": "Compra semanal"
        }
        """
            .formatted(OWNER_ID);

    MvcResult result =
        mockMvc
            .perform(
                post("/lists")
                    .header(CORRELATION_ID_HEADER, correlationId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isCreated())
            .andReturn();

    String listId = result.getResponse().getHeader("Location").substring("/lists/".length());

    assertThat(eventsFor(correlationId)).isEmpty();

    TestTransaction.flagForCommit();
    TestTransaction.end();

    List<ILoggingEvent> events = eventsFor(correlationId);
    assertThat(events).hasSize(1);
    assertThat(events.get(0).getFormattedMessage())
        .contains(
            "\"eventType\":\"list.created\"",
            "\"correlationId\":\"" + correlationId + "\"",
            "\"listId\":\"" + listId + "\"",
            "\"ownerId\":\"" + OWNER_ID + "\"",
            "\"name\":\"Compra semanal\"");
  }

  /**
   * Comprueba que un alta sin cabecera de correlación entrega el evento con el identificador
   * generado que el filtro devuelve en la respuesta.
   *
   * @throws Exception si la petición HTTP simulada falla
   */
  @Test
  void createList_withoutCorrelationHeader_usesGeneratedCorrelationId() throws Exception {
    String body =
        """
        {
          "ownerId": "%s",
          "name": "Compra sin correlación"
        }
        """
            .formatted(OWNER_ID);

    MvcResult result =
        mockMvc
            .perform(post("/lists").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated())
            .andReturn();

    String correlationId = result.getResponse().getHeader(CORRELATION_ID_HEADER);
    assertThat(correlationId).isNotBlank();

    assertThat(eventsFor(correlationId)).isEmpty();

    TestTransaction.flagForCommit();
    TestTransaction.end();

    assertThat(eventsFor(correlationId)).hasSize(1);
  }

  /**
   * Comprueba que un renombrado entrega, tras el commit, un evento {@code list.renamed} con el
   * nombre anterior y el nuevo.
   *
   * @throws Exception si la petición HTTP simulada falla
   */
  @Test
  void patchList_withCorrelationHeader_publishesRenamedEventAfterCommit() throws Exception {
    String correlationId = "corr-renamed";
    ShoppingList list = seedList("Nombre antiguo");
    String body =
        """
        {
          "ownerId": "%s",
          "name": "Nombre nuevo"
        }
        """
            .formatted(OWNER_ID);

    mockMvc
        .perform(
            patch("/lists/" + list.getPublicId())
                .header(CORRELATION_ID_HEADER, correlationId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk());

    assertThat(eventsFor(correlationId)).isEmpty();

    TestTransaction.flagForCommit();
    TestTransaction.end();

    List<ILoggingEvent> events = eventsFor(correlationId);
    assertThat(events).hasSize(1);
    assertThat(events.get(0).getFormattedMessage())
        .contains(
            "\"eventType\":\"list.renamed\"",
            "\"oldName\":\"Nombre antiguo\"",
            "\"newName\":\"Nombre nuevo\"");
  }

  /**
   * Comprueba que un borrado entrega, tras el commit, un evento {@code list.deleted} con el
   * identificador, el propietario y el nombre de la lista.
   *
   * @throws Exception si la petición HTTP simulada falla
   */
  @Test
  void deleteList_withCorrelationHeader_publishesDeletedEventAfterCommit() throws Exception {
    String correlationId = "corr-deleted";
    ShoppingList list = seedList("Para borrar");

    mockMvc
        .perform(
            delete("/lists/" + list.getPublicId())
                .param("ownerId", OWNER_ID.toString())
                .header(CORRELATION_ID_HEADER, correlationId))
        .andExpect(status().isNoContent());

    assertThat(eventsFor(correlationId)).isEmpty();

    TestTransaction.flagForCommit();
    TestTransaction.end();

    List<ILoggingEvent> events = eventsFor(correlationId);
    assertThat(events).hasSize(1);
    assertThat(events.get(0).getFormattedMessage())
        .contains(
            "\"eventType\":\"list.deleted\"",
            "\"listId\":\"" + list.getPublicId() + "\"",
            "\"ownerId\":\"" + OWNER_ID + "\"",
            "\"name\":\"Para borrar\"");
  }

  /**
   * Siembra una lista en la transacción del test y limpia el contexto de persistencia para que las
   * peticiones posteriores la lean desde la base de datos.
   *
   * @param name nombre de la lista a sembrar
   * @return entidad persistida con su identificador público asignado
   */
  private ShoppingList seedList(String name) {
    ShoppingList list = new ShoppingList();
    list.setOwnerId(OWNER_ID);
    list.setName(name);
    shoppingListRepository.saveAndFlush(list);
    entityManager.clear();
    return list;
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
