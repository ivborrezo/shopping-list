package dev.ivborrezo.shoppinglist.list.service.list.repository;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ivborrezo.shoppinglist.list.service.list.entity.ShoppingList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.TestConstructor.AutowireMode;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Test de integración de {@link ShoppingListRepository}.
 *
 * <p>Verifica el filtrado por propietario, la paginación y el orden por actividad ({@code
 * updated_at DESC, id DESC}), además de la búsqueda por identificador público, contra PostgreSQL
 * real (Testcontainers).
 */
@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@TestConstructor(autowireMode = AutowireMode.ALL)
@Transactional
@Testcontainers
class ShoppingListRepositoryIntegrationIT {

  @Container @ServiceConnection
  static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

  private static final UUID OWNER_ID = UUID.fromString("aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee");

  private static final UUID OTHER_OWNER_ID =
      UUID.fromString("aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeef");

  private final ShoppingListRepository repository;

  private final JdbcTemplate jdbcTemplate;

  /**
   * Inyecta las dependencias por constructor, sin {@code @Autowired} por campo.
   *
   * @param repository repositorio de listas bajo test
   * @param jdbcTemplate cliente JDBC para igualar marcas de auditoría en el test de desempate
   */
  ShoppingListRepositoryIntegrationIT(
      ShoppingListRepository repository, JdbcTemplate jdbcTemplate) {
    this.repository = repository;
    this.jdbcTemplate = jdbcTemplate;
  }

  /**
   * Comprueba que solo se devuelven las listas del propietario y que el orden es por actividad
   * descendente.
   *
   * @throws InterruptedException si el hilo se interrumpe durante la espera
   */
  @Test
  void findByOwnerId_returnsOnlyOwnerListsOrderedByUpdatedAtDesc() throws InterruptedException {
    ShoppingList first = repository.saveAndFlush(buildList(OWNER_ID, "Primera"));
    Thread.sleep(10);
    ShoppingList second = repository.saveAndFlush(buildList(OWNER_ID, "Segunda"));
    repository.saveAndFlush(buildList(OTHER_OWNER_ID, "De otro"));

    Page<ShoppingList> page =
        repository.findByOwnerIdOrderByUpdatedAtDescIdDesc(OWNER_ID, PageRequest.of(0, 10));

    assertThat(page.getTotalElements()).isEqualTo(2);
    assertThat(page.getContent())
        .extracting(ShoppingList::getPublicId)
        .containsExactly(second.getPublicId(), first.getPublicId());
  }

  /** Comprueba que la paginación reparte el total de listas del propietario. */
  @Test
  void findByOwnerId_paginatesResults() {
    repository.saveAndFlush(buildList(OWNER_ID, "Uno"));
    repository.saveAndFlush(buildList(OWNER_ID, "Dos"));
    repository.saveAndFlush(buildList(OWNER_ID, "Tres"));

    Page<ShoppingList> firstPage =
        repository.findByOwnerIdOrderByUpdatedAtDescIdDesc(OWNER_ID, PageRequest.of(0, 2));
    Page<ShoppingList> secondPage =
        repository.findByOwnerIdOrderByUpdatedAtDescIdDesc(OWNER_ID, PageRequest.of(1, 2));

    assertThat(firstPage.getContent()).hasSize(2);
    assertThat(firstPage.getTotalElements()).isEqualTo(3);
    assertThat(secondPage.getContent()).hasSize(1);
  }

  /** Comprueba que, con la misma marca de actividad, el desempate es por id interno descendente. */
  @Test
  void findByOwnerId_breaksTiesByIdDescending() {
    ShoppingList first = repository.saveAndFlush(buildList(OWNER_ID, "Primera"));
    ShoppingList second = repository.saveAndFlush(buildList(OWNER_ID, "Segunda"));

    jdbcTemplate.update(
        "UPDATE list SET updated_at = CURRENT_TIMESTAMP WHERE owner_id = ?", OWNER_ID);

    List<ShoppingList> content =
        repository
            .findByOwnerIdOrderByUpdatedAtDescIdDesc(OWNER_ID, PageRequest.of(0, 10))
            .getContent();

    assertThat(content)
        .extracting(ShoppingList::getPublicId)
        .containsExactly(second.getPublicId(), first.getPublicId());
  }

  /** Comprueba la búsqueda por identificador público (existente e inexistente). */
  @Test
  void findByPublicId_returnsMatchingList() {
    ShoppingList list = repository.saveAndFlush(buildList(OWNER_ID, "Buscada"));

    assertThat(repository.findByPublicId(list.getPublicId())).contains(list);
    assertThat(repository.findByPublicId(UUID.randomUUID())).isEmpty();
  }

  /**
   * Construye una lista sin persistir.
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
}
