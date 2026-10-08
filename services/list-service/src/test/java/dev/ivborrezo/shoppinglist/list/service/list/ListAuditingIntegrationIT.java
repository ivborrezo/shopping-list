package dev.ivborrezo.shoppinglist.list.service.list;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ivborrezo.shoppinglist.list.service.common.ProductType;
import dev.ivborrezo.shoppinglist.list.service.list.entity.ListItem;
import dev.ivborrezo.shoppinglist.list.service.list.entity.ShoppingList;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.jpa.test.autoconfigure.AutoConfigureTestEntityManager;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.TestConstructor.AutowireMode;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Test de integración del contexto y de la auditoría de la capa de persistencia de listas.
 *
 * <p>Arranca el contexto de Spring contra PostgreSQL real (Testcontainers), comprobando que Flyway
 * aplica las migraciones {@code V1}/{@code V2}, que las entidades mapean y persisten, que la
 * auditoría rellena las marcas de tiempo y que el identificador público (UUID v7) se asigna en
 * {@code @PrePersist}.
 */
@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@AutoConfigureTestEntityManager
@TestConstructor(autowireMode = AutowireMode.ALL)
@Transactional
@Testcontainers
class ListAuditingIntegrationIT {

  @Container @ServiceConnection
  static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

  private static final UUID OWNER_ID = UUID.fromString("aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee");

  private final TestEntityManager entityManager;

  /**
   * Inyecta las dependencias de test por constructor, sin {@code @Autowired} por campo, coherente
   * con la convención del resto del monorepo.
   *
   * @param entityManager gestor JPA para persistir entidades dentro de la transacción del test
   */
  ListAuditingIntegrationIT(TestEntityManager entityManager) {
    this.entityManager = entityManager;
  }

  /**
   * Persiste una lista nueva y comprueba que recibe un id interno, un identificador público UUID v7
   * y marcas de auditoría.
   */
  @Test
  void persist_list_assignsVersion7PublicIdAndTimestamps() {
    ShoppingList list = buildList("Compra semanal");

    entityManager.persistAndFlush(list);

    assertThat(list.getId()).isNotNull();
    assertThat(list.getPublicId()).isNotNull();
    assertThat(list.getPublicId().version()).isEqualTo(7);
    assertThat(list.getCreatedAt()).isNotNull();
    assertThat(list.getUpdatedAt()).isNotNull();
  }

  /**
   * Persiste un ítem asociado a una lista por su id interno y comprueba que recibe identificador
   * público UUID v7 y marcas de auditoría.
   */
  @Test
  void persist_item_assignsVersion7PublicIdAndTimestamps() {
    ShoppingList list = buildList("Compra con items");
    entityManager.persistAndFlush(list);

    ListItem item = buildItem(list.getId(), ProductType.BASE);
    entityManager.persistAndFlush(item);

    assertThat(item.getId()).isNotNull();
    assertThat(item.getPublicId()).isNotNull();
    assertThat(item.getPublicId().version()).isEqualTo(7);
    assertThat(item.getCreatedAt()).isNotNull();
    assertThat(item.getUpdatedAt()).isNotNull();
  }

  /**
   * Modifica el nombre de una lista persistida y comprueba que la auditoría refresca {@code
   * updatedAt}.
   *
   * @throws InterruptedException si el hilo se interrumpe durante la espera
   */
  @Test
  void update_list_refreshesUpdatedAt() throws InterruptedException {
    ShoppingList list = buildList("Compra antes");
    entityManager.persistAndFlush(list);
    Instant before = list.getUpdatedAt();

    Thread.sleep(10);
    list.setName("Compra despues");
    entityManager.flush();

    assertThat(list.getUpdatedAt()).isAfter(before);
  }

  /**
   * Construye una lista sin persistir para el propietario de test.
   *
   * @param name nombre de la lista
   * @return entidad {@link ShoppingList} sin persistir
   */
  private ShoppingList buildList(String name) {
    ShoppingList list = new ShoppingList();
    list.setOwnerId(OWNER_ID);
    list.setName(name);
    return list;
  }

  /**
   * Construye un ítem sin persistir asociado a la lista indicada.
   *
   * @param listId id interno de la lista propietaria
   * @param productType tipo de producto referenciado
   * @return entidad {@link ListItem} sin persistir
   */
  private ListItem buildItem(Long listId, ProductType productType) {
    ListItem item = new ListItem();
    item.setListId(listId);
    item.setProductId(UUID.randomUUID());
    item.setProductType(productType);
    item.setDisplayName("Leche");
    item.setPurchased(false);
    return item;
  }
}
