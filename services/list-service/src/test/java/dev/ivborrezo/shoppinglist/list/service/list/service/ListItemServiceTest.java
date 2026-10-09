package dev.ivborrezo.shoppinglist.list.service.list.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.ivborrezo.shoppinglist.list.service.common.BusinessException;
import dev.ivborrezo.shoppinglist.list.service.common.ErrorCode;
import dev.ivborrezo.shoppinglist.list.service.common.ProductType;
import dev.ivborrezo.shoppinglist.list.service.common.event.DomainEvent;
import dev.ivborrezo.shoppinglist.list.service.common.event.DomainEventPublisher;
import dev.ivborrezo.shoppinglist.list.service.common.event.EventType;
import dev.ivborrezo.shoppinglist.list.service.list.client.ProductCatalogClient;
import dev.ivborrezo.shoppinglist.list.service.list.dto.AddListItemRequest;
import dev.ivborrezo.shoppinglist.list.service.list.dto.ListItemResponse;
import dev.ivborrezo.shoppinglist.list.service.list.dto.UpdateListItemPurchasedRequest;
import dev.ivborrezo.shoppinglist.list.service.list.entity.ListItem;
import dev.ivborrezo.shoppinglist.list.service.list.entity.ShoppingList;
import dev.ivborrezo.shoppinglist.list.service.list.event.ListItemAddedEvent;
import dev.ivborrezo.shoppinglist.list.service.list.event.ListItemPurchasedEvent;
import dev.ivborrezo.shoppinglist.list.service.list.event.ListItemRemovedEvent;
import dev.ivborrezo.shoppinglist.list.service.list.repository.ListItemRepository;
import dev.ivborrezo.shoppinglist.list.service.list.repository.ShoppingListRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Test unitario de {@link ListItemService} con repositorios y catálogo mockeados.
 *
 * <p>Cubre el alta con snapshot resuelto, la precedencia de errores, la traducción de la violación
 * de unicidad a {@code DUPLICATE_LIST_ITEM}, el cambio de estado de compra, el borrado y la
 * publicación de los eventos de dominio asociados a cada mutación. La actividad real ({@code
 * updatedAt}) se verifica en el test de integración; aquí basta con comprobar que {@code touch}
 * deja la marca asignada.
 */
@ExtendWith(MockitoExtension.class)
class ListItemServiceTest {

  private static final UUID OWNER_ID = UUID.fromString("aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee");

  private static final UUID OTHER_OWNER_ID =
      UUID.fromString("aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeef");

  private static final String CORRELATION_ID = "corr-test";

  private static final Instant FIXED_INSTANT = Instant.parse("2026-01-01T00:00:00Z");

  @Mock private ShoppingListRepository shoppingListRepository;

  @Mock private ListItemRepository listItemRepository;

  @Mock private ProductCatalogClient productCatalogClient;

  @Mock private DomainEventPublisher domainEventPublisher;

  private Clock clock;

  private ListItemService listItemService;

  @BeforeEach
  void setUp() {
    clock = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);
    listItemService =
        new ListItemService(
            shoppingListRepository,
            listItemRepository,
            productCatalogClient,
            clock,
            domainEventPublisher);
    MDC.put("correlationId", CORRELATION_ID);
  }

  @AfterEach
  void tearDown() {
    MDC.clear();
  }

  /**
   * Añade un producto y comprueba que persiste la referencia, el snapshot resuelto y el estado
   * inicial, y que marca la lista como modificada.
   */
  @Test
  void add_validRequest_persistsSnapshotAndReturnsResponse() {
    UUID listPublicId = UUID.randomUUID();
    UUID productId = UUID.randomUUID();
    UUID itemPublicId = UUID.randomUUID();
    ShoppingList list = list(1L, listPublicId, OWNER_ID);
    when(shoppingListRepository.findByPublicId(listPublicId)).thenReturn(Optional.of(list));
    when(productCatalogClient.resolveDisplayName(ProductType.BASE, productId)).thenReturn("Leche");
    when(listItemRepository.saveAndFlush(any(ListItem.class)))
        .thenAnswer(
            invocation -> {
              ListItem toSave = invocation.getArgument(0);
              toSave.setPublicId(itemPublicId);
              return toSave;
            });

    ListItemResponse response =
        listItemService.add(listPublicId, OWNER_ID, new AddListItemRequest(productId, "BASE"));

    ArgumentCaptor<ListItem> captor = ArgumentCaptor.forClass(ListItem.class);
    verify(listItemRepository).saveAndFlush(captor.capture());
    ListItem persisted = captor.getValue();
    assertThat(persisted.getListId()).isEqualTo(1L);
    assertThat(persisted.getProductId()).isEqualTo(productId);
    assertThat(persisted.getProductType()).isEqualTo(ProductType.BASE);
    assertThat(persisted.getDisplayName()).isEqualTo("Leche");
    assertThat(persisted.getPurchased()).isFalse();

    verify(productCatalogClient).resolveDisplayName(ProductType.BASE, productId);
    assertThat(list.getUpdatedAt()).isNotNull();

    assertThat(response.id()).isEqualTo(itemPublicId);
    assertThat(response.productId()).isEqualTo(productId);
    assertThat(response.productType()).isEqualTo(ProductType.BASE);
    assertThat(response.displayName()).isEqualTo("Leche");
    assertThat(response.purchased()).isFalse();

    DomainEvent<?> event = capturePublishedEvent();
    assertThat(event.eventType()).isEqualTo(EventType.LIST_ITEM_ADDED);
    assertThat(event.correlationId()).isEqualTo(CORRELATION_ID);
    assertThat(event.occurredAt()).isEqualTo(FIXED_INSTANT);
    assertThat(event.payload())
        .isEqualTo(
            new ListItemAddedEvent(
                listPublicId, itemPublicId, ProductType.BASE, productId, "Leche"));
  }

  /** Rechaza un tipo de producto no soportado sin llegar a consultar el catálogo. */
  @Test
  void add_invalidProductType_throwsInvalidProductTypeWithoutResolvingProduct() {
    UUID listPublicId = UUID.randomUUID();
    ShoppingList list = list(1L, listPublicId, OWNER_ID);
    when(shoppingListRepository.findByPublicId(listPublicId)).thenReturn(Optional.of(list));

    assertThatThrownBy(
            () ->
                listItemService.add(
                    listPublicId, OWNER_ID, new AddListItemRequest(UUID.randomUUID(), "OTRO")))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_PRODUCT_TYPE));

    verifyNoInteractions(productCatalogClient);
    verify(listItemRepository, never()).saveAndFlush(any(ListItem.class));
  }

  /**
   * Devuelve {@code LIST_NOT_FOUND} antes que {@code OWNER_MISMATCH} cuando la lista no existe, sea
   * cual sea el {@code ownerId} recibido.
   */
  @Test
  void add_missingList_throwsListNotFoundBeforeOwnerCheck() {
    UUID listPublicId = UUID.randomUUID();
    when(shoppingListRepository.findByPublicId(listPublicId)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                listItemService.add(
                    listPublicId,
                    OTHER_OWNER_ID,
                    new AddListItemRequest(UUID.randomUUID(), "BASE")))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.LIST_NOT_FOUND));

    verifyNoInteractions(productCatalogClient);
  }

  /** Rechaza el alta en una lista ajena con {@code OWNER_MISMATCH} sin consultar el catálogo. */
  @Test
  void add_otherOwner_throwsOwnerMismatch() {
    UUID listPublicId = UUID.randomUUID();
    ShoppingList list = list(1L, listPublicId, OWNER_ID);
    when(shoppingListRepository.findByPublicId(listPublicId)).thenReturn(Optional.of(list));

    assertThatThrownBy(
            () ->
                listItemService.add(
                    listPublicId,
                    OTHER_OWNER_ID,
                    new AddListItemRequest(UUID.randomUUID(), "BASE")))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.OWNER_MISMATCH));

    verifyNoInteractions(productCatalogClient);
  }

  /** Traduce la violación de la unicidad al persistir el ítem a {@code DUPLICATE_LIST_ITEM}. */
  @Test
  void add_duplicateProduct_throwsDuplicateListItem() {
    UUID listPublicId = UUID.randomUUID();
    UUID productId = UUID.randomUUID();
    ShoppingList list = list(1L, listPublicId, OWNER_ID);
    when(shoppingListRepository.findByPublicId(listPublicId)).thenReturn(Optional.of(list));
    when(productCatalogClient.resolveDisplayName(ProductType.USER, productId)).thenReturn("Leche");
    when(listItemRepository.saveAndFlush(any(ListItem.class)))
        .thenThrow(new DataIntegrityViolationException("duplicate"));

    assertThatThrownBy(
            () ->
                listItemService.add(
                    listPublicId, OWNER_ID, new AddListItemRequest(productId, "USER")))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.DUPLICATE_LIST_ITEM));

    verify(domainEventPublisher, never()).publish(any());
  }

  /** Propaga como fallo cerrado la referencia de producto inválida que lanza el catálogo. */
  @Test
  void add_invalidProductReference_propagatesFailure() {
    UUID listPublicId = UUID.randomUUID();
    UUID productId = UUID.randomUUID();
    ShoppingList list = list(1L, listPublicId, OWNER_ID);
    when(shoppingListRepository.findByPublicId(listPublicId)).thenReturn(Optional.of(list));
    when(productCatalogClient.resolveDisplayName(ProductType.BASE, productId))
        .thenThrow(new BusinessException(ErrorCode.INVALID_PRODUCT_REFERENCE));

    assertThatThrownBy(
            () ->
                listItemService.add(
                    listPublicId, OWNER_ID, new AddListItemRequest(productId, "BASE")))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_PRODUCT_REFERENCE));

    verify(listItemRepository, never()).saveAndFlush(any(ListItem.class));
  }

  /** Propaga como fallo cerrado la indisponibilidad del catálogo. */
  @Test
  void add_productServiceUnavailable_propagatesFailure() {
    UUID listPublicId = UUID.randomUUID();
    UUID productId = UUID.randomUUID();
    ShoppingList list = list(1L, listPublicId, OWNER_ID);
    when(shoppingListRepository.findByPublicId(listPublicId)).thenReturn(Optional.of(list));
    when(productCatalogClient.resolveDisplayName(ProductType.BASE, productId))
        .thenThrow(new BusinessException(ErrorCode.PRODUCT_SERVICE_UNAVAILABLE));

    assertThatThrownBy(
            () ->
                listItemService.add(
                    listPublicId, OWNER_ID, new AddListItemRequest(productId, "BASE")))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PRODUCT_SERVICE_UNAVAILABLE));
  }

  /** Cambia el estado de compra del ítem y marca la lista como modificada. */
  @Test
  void updatePurchased_validRequest_updatesStateAndTouchesList() {
    UUID listPublicId = UUID.randomUUID();
    UUID itemPublicId = UUID.randomUUID();
    UUID productId = UUID.randomUUID();
    ShoppingList list = list(1L, listPublicId, OWNER_ID);
    ListItem item = item(1L, itemPublicId, productId, ProductType.BASE, "Leche", false);
    when(shoppingListRepository.findByPublicId(listPublicId)).thenReturn(Optional.of(list));
    when(listItemRepository.findByPublicId(itemPublicId)).thenReturn(Optional.of(item));
    when(listItemRepository.save(item)).thenAnswer(invocation -> invocation.getArgument(0));

    ListItemResponse response =
        listItemService.updatePurchased(
            listPublicId, itemPublicId, OWNER_ID, new UpdateListItemPurchasedRequest(true));

    assertThat(item.getPurchased()).isTrue();
    assertThat(list.getUpdatedAt()).isNotNull();
    verify(listItemRepository).save(item);
    assertThat(response.id()).isEqualTo(itemPublicId);
    assertThat(response.purchased()).isTrue();

    DomainEvent<?> event = capturePublishedEvent();
    assertThat(event.eventType()).isEqualTo(EventType.LIST_ITEM_PURCHASED);
    assertThat(event.correlationId()).isEqualTo(CORRELATION_ID);
    assertThat(event.occurredAt()).isEqualTo(FIXED_INSTANT);
    assertThat(event.payload())
        .isEqualTo(
            new ListItemPurchasedEvent(
                listPublicId, itemPublicId, ProductType.BASE, productId, "Leche", true));
  }

  /** Traduce la ausencia del ítem a {@code LIST_ITEM_NOT_FOUND}. */
  @Test
  void updatePurchased_missingItem_throwsListItemNotFound() {
    UUID listPublicId = UUID.randomUUID();
    UUID itemPublicId = UUID.randomUUID();
    ShoppingList list = list(1L, listPublicId, OWNER_ID);
    when(shoppingListRepository.findByPublicId(listPublicId)).thenReturn(Optional.of(list));
    when(listItemRepository.findByPublicId(itemPublicId)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                listItemService.updatePurchased(
                    listPublicId, itemPublicId, OWNER_ID, new UpdateListItemPurchasedRequest(true)))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.LIST_ITEM_NOT_FOUND));
  }

  /**
   * Trata un ítem que existe pero pertenece a otra lista como {@code LIST_ITEM_NOT_FOUND}, sin
   * exponerlo a través de la lista indicada.
   */
  @Test
  void updatePurchased_itemFromOtherList_throwsListItemNotFound() {
    UUID listPublicId = UUID.randomUUID();
    UUID itemPublicId = UUID.randomUUID();
    ShoppingList list = list(1L, listPublicId, OWNER_ID);
    ListItem foreign = item(2L, itemPublicId, UUID.randomUUID(), ProductType.BASE, "Leche", false);
    when(shoppingListRepository.findByPublicId(listPublicId)).thenReturn(Optional.of(list));
    when(listItemRepository.findByPublicId(itemPublicId)).thenReturn(Optional.of(foreign));

    assertThatThrownBy(
            () ->
                listItemService.updatePurchased(
                    listPublicId, itemPublicId, OWNER_ID, new UpdateListItemPurchasedRequest(true)))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.LIST_ITEM_NOT_FOUND));

    verify(listItemRepository, never()).save(any(ListItem.class));
  }

  /** Elimina el ítem y marca la lista como modificada. */
  @Test
  void remove_validRequest_deletesItemAndTouchesList() {
    UUID listPublicId = UUID.randomUUID();
    UUID itemPublicId = UUID.randomUUID();
    UUID productId = UUID.randomUUID();
    ShoppingList list = list(1L, listPublicId, OWNER_ID);
    ListItem item = item(1L, itemPublicId, productId, ProductType.BASE, "Leche", false);
    when(shoppingListRepository.findByPublicId(listPublicId)).thenReturn(Optional.of(list));
    when(listItemRepository.findByPublicId(itemPublicId)).thenReturn(Optional.of(item));

    listItemService.remove(listPublicId, itemPublicId, OWNER_ID);

    verify(listItemRepository).delete(item);
    assertThat(list.getUpdatedAt()).isNotNull();

    DomainEvent<?> event = capturePublishedEvent();
    assertThat(event.eventType()).isEqualTo(EventType.LIST_ITEM_REMOVED);
    assertThat(event.correlationId()).isEqualTo(CORRELATION_ID);
    assertThat(event.occurredAt()).isEqualTo(FIXED_INSTANT);
    assertThat(event.payload())
        .isEqualTo(
            new ListItemRemovedEvent(
                listPublicId, itemPublicId, ProductType.BASE, productId, "Leche"));
  }

  /** Traduce la ausencia del ítem en el borrado a {@code LIST_ITEM_NOT_FOUND}. */
  @Test
  void remove_missingItem_throwsListItemNotFound() {
    UUID listPublicId = UUID.randomUUID();
    UUID itemPublicId = UUID.randomUUID();
    ShoppingList list = list(1L, listPublicId, OWNER_ID);
    when(shoppingListRepository.findByPublicId(listPublicId)).thenReturn(Optional.of(list));
    when(listItemRepository.findByPublicId(itemPublicId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> listItemService.remove(listPublicId, itemPublicId, OWNER_ID))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.LIST_ITEM_NOT_FOUND));

    verify(listItemRepository, never()).delete(any(ListItem.class));
  }

  /** Rechaza el borrado de un ítem de una lista ajena con {@code OWNER_MISMATCH}. */
  @Test
  void remove_otherOwner_throwsOwnerMismatch() {
    UUID listPublicId = UUID.randomUUID();
    UUID itemPublicId = UUID.randomUUID();
    ShoppingList list = list(1L, listPublicId, OWNER_ID);
    when(shoppingListRepository.findByPublicId(listPublicId)).thenReturn(Optional.of(list));

    assertThatThrownBy(() -> listItemService.remove(listPublicId, itemPublicId, OTHER_OWNER_ID))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.OWNER_MISMATCH));

    verify(listItemRepository, never()).delete(any(ListItem.class));
  }

  private DomainEvent<?> capturePublishedEvent() {
    ArgumentCaptor<DomainEvent<?>> captor = ArgumentCaptor.forClass(DomainEvent.class);
    verify(domainEventPublisher).publish(captor.capture());
    return captor.getValue();
  }

  private static ShoppingList list(Long id, UUID publicId, UUID ownerId) {
    ShoppingList shoppingList = new ShoppingList();
    shoppingList.setId(id);
    shoppingList.setPublicId(publicId);
    shoppingList.setOwnerId(ownerId);
    shoppingList.setName("Compra semanal");
    return shoppingList;
  }

  private static ListItem item(
      Long listId,
      UUID publicId,
      UUID productId,
      ProductType productType,
      String displayName,
      boolean purchased) {
    ListItem item = new ListItem();
    item.setListId(listId);
    item.setPublicId(publicId);
    item.setProductId(productId);
    item.setProductType(productType);
    item.setDisplayName(displayName);
    item.setPurchased(purchased);
    return item;
  }
}
