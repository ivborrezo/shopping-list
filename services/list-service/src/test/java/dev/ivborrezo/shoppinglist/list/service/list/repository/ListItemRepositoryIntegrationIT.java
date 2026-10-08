package dev.ivborrezo.shoppinglist.list.service.list.repository;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ivborrezo.shoppinglist.list.service.common.ProductType;
import dev.ivborrezo.shoppinglist.list.service.list.entity.ListItem;
import dev.ivborrezo.shoppinglist.list.service.list.entity.ShoppingList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
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
 * Test de integración de {@link ListItemRepository}.
 *
 * <p>Verifica el orden {@code purchased ASC, id ASC}, el filtrado por lista y la búsqueda por
 * identificador público contra PostgreSQL real (Testcontainers).
 */
@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@TestConstructor(autowireMode = AutowireMode.ALL)
@Transactional
@Testcontainers
class ListItemRepositoryIntegrationIT {

  @Container @ServiceConnection
  static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

  private static final UUID OWNER_ID = UUID.fromString("aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee");

  private final ShoppingListRepository shoppingListRepository;

  private final ListItemRepository repository;

  /**
   * Inyecta las dependencias por constructor, sin {@code @Autowired} por campo.
   *
   * @param shoppingListRepository repositorio de listas para preparar la lista padre
   * @param repository repositorio de ítems bajo test
   */
  ListItemRepositoryIntegrationIT(
      ShoppingListRepository shoppingListRepository, ListItemRepository repository) {
    this.shoppingListRepository = shoppingListRepository;
    this.repository = repository;
  }

  /** Comprueba que los ítems pendientes van primero y que dentro de cada grupo ordenan por id. */
  @Test
  void findByListId_returnsPendingItemsFirstThenById() {
    ShoppingList list = shoppingListRepository.saveAndFlush(buildList());
    ListItem pendingFirst = repository.saveAndFlush(buildItem(list.getId(), false, "Leche"));
    ListItem purchased = repository.saveAndFlush(buildItem(list.getId(), true, "Pan"));
    ListItem pendingSecond = repository.saveAndFlush(buildItem(list.getId(), false, "Huevos"));

    List<ListItem> items = repository.findByListIdOrderByPurchasedAscIdAsc(list.getId());

    assertThat(items)
        .extracting(ListItem::getPublicId)
        .containsExactly(
            pendingFirst.getPublicId(), pendingSecond.getPublicId(), purchased.getPublicId());
  }

  /** Comprueba que solo se devuelven los ítems de la lista consultada. */
  @Test
  void findByListId_returnsOnlyItemsOfTheList() {
    ShoppingList list = shoppingListRepository.saveAndFlush(buildList());
    ShoppingList other = shoppingListRepository.saveAndFlush(buildList());
    repository.saveAndFlush(buildItem(list.getId(), false, "Leche"));
    repository.saveAndFlush(buildItem(other.getId(), false, "Pan"));

    List<ListItem> items = repository.findByListIdOrderByPurchasedAscIdAsc(list.getId());

    assertThat(items).hasSize(1);
    assertThat(items.get(0).getDisplayName()).isEqualTo("Leche");
  }

  /** Comprueba la búsqueda por identificador público (existente e inexistente). */
  @Test
  void findByPublicId_returnsMatchingItem() {
    ShoppingList list = shoppingListRepository.saveAndFlush(buildList());
    ListItem item = repository.saveAndFlush(buildItem(list.getId(), false, "Leche"));

    assertThat(repository.findByPublicId(item.getPublicId())).contains(item);
    assertThat(repository.findByPublicId(UUID.randomUUID())).isEmpty();
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
   * @param purchased estado de compra
   * @param displayName snapshot del nombre del producto
   * @return entidad {@link ListItem} sin persistir
   */
  private ListItem buildItem(Long listId, boolean purchased, String displayName) {
    ListItem item = new ListItem();
    item.setListId(listId);
    item.setProductId(UUID.randomUUID());
    item.setProductType(ProductType.BASE);
    item.setDisplayName(displayName);
    item.setPurchased(purchased);
    return item;
  }
}
