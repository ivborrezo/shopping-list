package dev.ivborrezo.shoppinglist.list.service.list.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ivborrezo.shoppinglist.list.service.common.ProductType;
import dev.ivborrezo.shoppinglist.list.service.list.entity.ListItem;
import dev.ivborrezo.shoppinglist.list.service.list.entity.ShoppingList;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.TestConstructor.AutowireMode;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Test de integración de las constraints del esquema de listas.
 *
 * <p>Verifica contra PostgreSQL real la unicidad de {@code public_id}, la unicidad de producto por
 * lista ({@code list_id, product_id, product_type}), el {@code CHECK} de {@code product_type} y el
 * borrado en cascada de los ítems al eliminar su lista.
 */
@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@TestConstructor(autowireMode = AutowireMode.ALL)
@Transactional
@Testcontainers
class ListConstraintsIntegrationIT {

  @Container @ServiceConnection
  static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

  private static final UUID OWNER_ID = UUID.fromString("aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee");

  private final ShoppingListRepository shoppingListRepository;

  private final ListItemRepository listItemRepository;

  private final JdbcTemplate jdbcTemplate;

  /**
   * Inyecta las dependencias por constructor.
   *
   * @param shoppingListRepository repositorio de listas
   * @param listItemRepository repositorio de ítems
   * @param jdbcTemplate cliente JDBC para forzar una inserción inválida a nivel de BD
   */
  ListConstraintsIntegrationIT(
      ShoppingListRepository shoppingListRepository,
      ListItemRepository listItemRepository,
      JdbcTemplate jdbcTemplate) {
    this.shoppingListRepository = shoppingListRepository;
    this.listItemRepository = listItemRepository;
    this.jdbcTemplate = jdbcTemplate;
  }

  /** Comprueba que dos listas con el mismo {@code public_id} violan la unicidad. */
  @Test
  void duplicate_listPublicId_violatesUniqueConstraint() {
    UUID duplicated = UUID.randomUUID();
    ShoppingList first = buildList();
    first.setPublicId(duplicated);
    shoppingListRepository.saveAndFlush(first);

    ShoppingList second = buildList();
    second.setPublicId(duplicated);

    assertThatThrownBy(() -> shoppingListRepository.saveAndFlush(second))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  /** Comprueba que repetir el mismo producto en la misma lista viola la unicidad compuesta. */
  @Test
  void duplicate_itemForSameProduct_violatesUniqueConstraint() {
    ShoppingList list = shoppingListRepository.saveAndFlush(buildList());
    UUID productId = UUID.randomUUID();
    listItemRepository.saveAndFlush(buildItem(list.getId(), productId, ProductType.BASE));

    ListItem duplicate = buildItem(list.getId(), productId, ProductType.BASE);

    assertThatThrownBy(() -> listItemRepository.saveAndFlush(duplicate))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  /** Comprueba que un {@code product_type} fuera de {@code BASE}/{@code USER} viola el CHECK. */
  @Test
  void invalid_productType_violatesCheckConstraint() {
    ShoppingList list = shoppingListRepository.saveAndFlush(buildList());

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "INSERT INTO list_item "
                        + "(public_id, list_id, product_id, product_type, display_name, purchased) "
                        + "VALUES (?, ?, ?, 'INVALID', 'Producto', FALSE)",
                    UUID.randomUUID(),
                    list.getId(),
                    UUID.randomUUID()))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  /** Comprueba que borrar una lista elimina sus ítems por la FK {@code ON DELETE CASCADE}. */
  @Test
  void deleting_list_cascadesToItems() {
    ShoppingList list = shoppingListRepository.saveAndFlush(buildList());
    listItemRepository.saveAndFlush(buildItem(list.getId(), UUID.randomUUID(), ProductType.BASE));
    listItemRepository.saveAndFlush(buildItem(list.getId(), UUID.randomUUID(), ProductType.USER));

    shoppingListRepository.deleteById(list.getId());
    shoppingListRepository.flush();

    assertThat(listItemRepository.count()).isZero();
  }

  /**
   * Construye una lista sin persistir para el propietario de test.
   *
   * @return entidad {@link ShoppingList} sin persistir
   */
  private ShoppingList buildList() {
    ShoppingList list = new ShoppingList();
    list.setOwnerId(OWNER_ID);
    list.setName("Lista");
    return list;
  }

  /**
   * Construye un ítem sin persistir.
   *
   * @param listId id interno de la lista propietaria
   * @param productId identificador público del producto referenciado
   * @param productType tipo de producto referenciado
   * @return entidad {@link ListItem} sin persistir
   */
  private ListItem buildItem(Long listId, UUID productId, ProductType productType) {
    ListItem item = new ListItem();
    item.setListId(listId);
    item.setProductId(productId);
    item.setProductType(productType);
    item.setDisplayName("Producto");
    item.setPurchased(false);
    return item;
  }
}
