package dev.ivborrezo.shoppinglist.list.service.list.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.ivborrezo.shoppinglist.list.service.common.BusinessException;
import dev.ivborrezo.shoppinglist.list.service.common.ErrorCode;
import dev.ivborrezo.shoppinglist.list.service.common.ProductType;
import dev.ivborrezo.shoppinglist.list.service.common.dto.PagedResponse;
import dev.ivborrezo.shoppinglist.list.service.common.event.DomainEvent;
import dev.ivborrezo.shoppinglist.list.service.common.event.DomainEventPublisher;
import dev.ivborrezo.shoppinglist.list.service.common.event.EventType;
import dev.ivborrezo.shoppinglist.list.service.list.dto.CreateListRequest;
import dev.ivborrezo.shoppinglist.list.service.list.dto.ShoppingListResponse;
import dev.ivborrezo.shoppinglist.list.service.list.dto.ShoppingListSummaryResponse;
import dev.ivborrezo.shoppinglist.list.service.list.dto.UpdateListRequest;
import dev.ivborrezo.shoppinglist.list.service.list.entity.ListItem;
import dev.ivborrezo.shoppinglist.list.service.list.entity.ShoppingList;
import dev.ivborrezo.shoppinglist.list.service.list.event.ListCreatedEvent;
import dev.ivborrezo.shoppinglist.list.service.list.event.ListDeletedEvent;
import dev.ivborrezo.shoppinglist.list.service.list.event.ListRenamedEvent;
import dev.ivborrezo.shoppinglist.list.service.list.repository.ListItemRepository;
import dev.ivborrezo.shoppinglist.list.service.list.repository.ShoppingListRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/**
 * Test unitario de {@link ShoppingListService} con repositorios mockeados.
 *
 * <p>Cubre el mapeo a los DTOs del contrato y la traducción de ausencias a {@code LIST_NOT_FOUND}.
 * No se asertan las marcas de auditoría porque, al no correr con JPA, no las rellena Spring Data.
 */
@ExtendWith(MockitoExtension.class)
class ShoppingListServiceTest {

  private static final UUID OWNER_ID = UUID.fromString("aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee");

  private static final UUID OTHER_OWNER_ID =
      UUID.fromString("aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeef");

  private static final Instant FIXED_INSTANT = Instant.parse("2026-01-02T03:04:05Z");

  private static final String CORRELATION_ID = "corr-test";

  @Mock private ShoppingListRepository shoppingListRepository;

  @Mock private ListItemRepository listItemRepository;

  @Mock private Clock clock;

  @Mock private DomainEventPublisher domainEventPublisher;

  private ShoppingListService shoppingListService;

  @BeforeEach
  void setUp() {
    lenient().when(clock.instant()).thenReturn(FIXED_INSTANT);
    MDC.put("correlationId", CORRELATION_ID);
    shoppingListService =
        new ShoppingListService(
            shoppingListRepository, listItemRepository, clock, domainEventPublisher);
  }

  @AfterEach
  void tearDown() {
    MDC.clear();
  }

  /** Crea una lista y comprueba que persiste propietario y nombre y devuelve el DTO esperado. */
  @Test
  void create_persistsOwnerAndName_andReturnsResponse() {
    UUID publicId = UUID.randomUUID();
    CreateListRequest request = new CreateListRequest(OWNER_ID, "Compra semanal");
    when(shoppingListRepository.save(any(ShoppingList.class)))
        .thenAnswer(
            invocation -> {
              ShoppingList saved = invocation.getArgument(0);
              saved.setId(1L);
              saved.setPublicId(publicId);
              return saved;
            });

    ShoppingListResponse response = shoppingListService.create(request);

    ArgumentCaptor<ShoppingList> captor = ArgumentCaptor.forClass(ShoppingList.class);
    verify(shoppingListRepository).save(captor.capture());
    assertThat(captor.getValue().getOwnerId()).isEqualTo(OWNER_ID);
    assertThat(captor.getValue().getName()).isEqualTo("Compra semanal");
    assertThat(response.id()).isEqualTo(publicId);
    assertThat(response.ownerId()).isEqualTo(OWNER_ID);
    assertThat(response.name()).isEqualTo("Compra semanal");
    assertThat(response.items()).isEmpty();
  }

  /**
   * Publica un evento {@code list.created} con el instante, la correlación y el payload de la lista
   * recién creada.
   */
  @Test
  void create_publishesListCreatedEvent() {
    UUID publicId = UUID.randomUUID();
    CreateListRequest request = new CreateListRequest(OWNER_ID, "Compra semanal");
    when(shoppingListRepository.save(any(ShoppingList.class)))
        .thenAnswer(
            invocation -> {
              ShoppingList saved = invocation.getArgument(0);
              saved.setId(1L);
              saved.setPublicId(publicId);
              return saved;
            });

    shoppingListService.create(request);

    ArgumentCaptor<DomainEvent<?>> captor = domainEventCaptor();
    verify(domainEventPublisher).publish(captor.capture());
    DomainEvent<?> event = captor.getValue();
    assertThat(event.eventType()).isEqualTo(EventType.LIST_CREATED);
    assertThat(event.occurredAt()).isEqualTo(FIXED_INSTANT);
    assertThat(event.correlationId()).isEqualTo(CORRELATION_ID);
    assertThat(event.payload())
        .isInstanceOfSatisfying(
            ListCreatedEvent.class,
            payload -> {
              assertThat(payload.listId()).isEqualTo(publicId);
              assertThat(payload.ownerId()).isEqualTo(OWNER_ID);
              assertThat(payload.name()).isEqualTo("Compra semanal");
            });
  }

  /** Lista las listas de un propietario como resúmenes y traslada la paginación de la página. */
  @Test
  void findPage_returnsSummariesWithPaginationMetadata() {
    ShoppingList first = list(1L, UUID.randomUUID(), OWNER_ID, "Primera");
    ShoppingList second = list(2L, UUID.randomUUID(), OWNER_ID, "Segunda");
    Pageable pageable = PageRequest.of(0, 2);
    when(shoppingListRepository.findByOwnerIdOrderByUpdatedAtDescIdDesc(OWNER_ID, pageable))
        .thenReturn(new PageImpl<>(List.of(first, second), pageable, 5));

    PagedResponse<ShoppingListSummaryResponse> page =
        shoppingListService.findPage(OWNER_ID, pageable);

    assertThat(page.content()).hasSize(2);
    assertThat(page.content().get(0).id()).isEqualTo(first.getPublicId());
    assertThat(page.content().get(0).name()).isEqualTo("Primera");
    assertThat(page.content().get(1).id()).isEqualTo(second.getPublicId());
    assertThat(page.page()).isZero();
    assertThat(page.size()).isEqualTo(2);
    assertThat(page.totalElements()).isEqualTo(5);
  }

  /**
   * Cuando el propietario no tiene listas, delega la consulta en el repositorio y devuelve una
   * página vacía con el total a cero.
   */
  @Test
  void findPage_whenNoLists_returnsEmptyPage() {
    Pageable pageable = PageRequest.of(0, 20);
    when(shoppingListRepository.findByOwnerIdOrderByUpdatedAtDescIdDesc(OWNER_ID, pageable))
        .thenReturn(new PageImpl<>(List.of(), pageable, 0));

    PagedResponse<ShoppingListSummaryResponse> page =
        shoppingListService.findPage(OWNER_ID, pageable);

    verify(shoppingListRepository).findByOwnerIdOrderByUpdatedAtDescIdDesc(OWNER_ID, pageable);
    assertThat(page.content()).isEmpty();
    assertThat(page.totalElements()).isZero();
  }

  /** Obtiene el detalle de una lista existente con sus ítems en el orden devuelto por el repo. */
  @Test
  void findById_existing_returnsListWithItemsInRepositoryOrder() {
    UUID publicId = UUID.randomUUID();
    ShoppingList shoppingList = list(1L, publicId, OWNER_ID, "Compra semanal");
    ListItem pending =
        item(UUID.randomUUID(), UUID.randomUUID(), ProductType.BASE, "Leche entera", false);
    ListItem purchased =
        item(UUID.randomUUID(), UUID.randomUUID(), ProductType.USER, "Pan integral", true);
    when(shoppingListRepository.findByPublicId(publicId)).thenReturn(Optional.of(shoppingList));
    when(listItemRepository.findByListIdOrderByPurchasedAscIdAsc(1L))
        .thenReturn(List.of(pending, purchased));

    ShoppingListResponse response = shoppingListService.findById(publicId);

    assertThat(response.id()).isEqualTo(publicId);
    assertThat(response.ownerId()).isEqualTo(OWNER_ID);
    assertThat(response.name()).isEqualTo("Compra semanal");
    assertThat(response.items())
        .extracting("displayName")
        .containsExactly("Leche entera", "Pan integral");
    assertThat(response.items().get(0).id()).isEqualTo(pending.getPublicId());
    assertThat(response.items().get(0).productId()).isEqualTo(pending.getProductId());
    assertThat(response.items().get(0).productType()).isEqualTo(ProductType.BASE);
    assertThat(response.items().get(0).purchased()).isFalse();
  }

  /** Traduce la ausencia de una lista a {@code LIST_NOT_FOUND}. */
  @Test
  void findById_missing_throwsNotFound() {
    UUID publicId = UUID.randomUUID();
    when(shoppingListRepository.findByPublicId(publicId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> shoppingListService.findById(publicId))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.LIST_NOT_FOUND));
  }

  /** Aplica el nombre nuevo de la petición y devuelve el detalle actualizado con sus ítems. */
  @Test
  void update_withName_appliesNewName() {
    UUID publicId = UUID.randomUUID();
    ShoppingList shoppingList = list(1L, publicId, OWNER_ID, "Nombre antiguo");
    when(shoppingListRepository.findByPublicId(publicId)).thenReturn(Optional.of(shoppingList));
    when(shoppingListRepository.save(any(ShoppingList.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(listItemRepository.findByListIdOrderByPurchasedAscIdAsc(1L)).thenReturn(List.of());

    ShoppingListResponse response =
        shoppingListService.update(publicId, new UpdateListRequest(OWNER_ID, "Nombre nuevo"));

    assertThat(shoppingList.getName()).isEqualTo("Nombre nuevo");
    assertThat(response.name()).isEqualTo("Nombre nuevo");
  }

  /**
   * Publica un evento {@code list.renamed} con el nombre anterior y el nuevo cuando la petición
   * incluye un nombre.
   */
  @Test
  void update_withName_publishesListRenamedEvent() {
    UUID publicId = UUID.randomUUID();
    ShoppingList shoppingList = list(1L, publicId, OWNER_ID, "Nombre antiguo");
    when(shoppingListRepository.findByPublicId(publicId)).thenReturn(Optional.of(shoppingList));
    when(shoppingListRepository.save(any(ShoppingList.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(listItemRepository.findByListIdOrderByPurchasedAscIdAsc(1L)).thenReturn(List.of());

    shoppingListService.update(publicId, new UpdateListRequest(OWNER_ID, "Nombre nuevo"));

    ArgumentCaptor<DomainEvent<?>> captor = domainEventCaptor();
    verify(domainEventPublisher).publish(captor.capture());
    DomainEvent<?> event = captor.getValue();
    assertThat(event.eventType()).isEqualTo(EventType.LIST_RENAMED);
    assertThat(event.occurredAt()).isEqualTo(FIXED_INSTANT);
    assertThat(event.correlationId()).isEqualTo(CORRELATION_ID);
    assertThat(event.payload())
        .isInstanceOfSatisfying(
            ListRenamedEvent.class,
            payload -> {
              assertThat(payload.listId()).isEqualTo(publicId);
              assertThat(payload.ownerId()).isEqualTo(OWNER_ID);
              assertThat(payload.oldName()).isEqualTo("Nombre antiguo");
              assertThat(payload.newName()).isEqualTo("Nombre nuevo");
            });
  }

  /** Conserva el nombre existente cuando la petición no incluye uno nuevo. */
  @Test
  void update_withoutName_keepsExistingName() {
    UUID publicId = UUID.randomUUID();
    ShoppingList shoppingList = list(1L, publicId, OWNER_ID, "Nombre original");
    when(shoppingListRepository.findByPublicId(publicId)).thenReturn(Optional.of(shoppingList));
    when(shoppingListRepository.save(any(ShoppingList.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(listItemRepository.findByListIdOrderByPurchasedAscIdAsc(1L)).thenReturn(List.of());

    ShoppingListResponse response =
        shoppingListService.update(publicId, new UpdateListRequest(OWNER_ID, null));

    assertThat(response.name()).isEqualTo("Nombre original");
  }

  /** No publica ningún evento cuando la petición de actualización no incluye un nombre nuevo. */
  @Test
  void update_withoutName_doesNotPublish() {
    UUID publicId = UUID.randomUUID();
    ShoppingList shoppingList = list(1L, publicId, OWNER_ID, "Nombre original");
    when(shoppingListRepository.findByPublicId(publicId)).thenReturn(Optional.of(shoppingList));
    when(shoppingListRepository.save(any(ShoppingList.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(listItemRepository.findByListIdOrderByPurchasedAscIdAsc(1L)).thenReturn(List.of());

    shoppingListService.update(publicId, new UpdateListRequest(OWNER_ID, null));

    verify(domainEventPublisher, never()).publish(any(DomainEvent.class));
  }

  /** Traduce la ausencia de una lista en el renombrado a {@code LIST_NOT_FOUND}. */
  @Test
  void update_missing_throwsNotFound() {
    UUID publicId = UUID.randomUUID();
    when(shoppingListRepository.findByPublicId(publicId)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> shoppingListService.update(publicId, new UpdateListRequest(OWNER_ID, "Nuevo")))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.LIST_NOT_FOUND));
  }

  /** Elimina la lista existente a través del repositorio. */
  @Test
  void delete_existing_deletesList() {
    UUID publicId = UUID.randomUUID();
    ShoppingList shoppingList = list(1L, publicId, OWNER_ID, "A borrar");
    when(shoppingListRepository.findByPublicId(publicId)).thenReturn(Optional.of(shoppingList));

    shoppingListService.delete(publicId, OWNER_ID);

    verify(shoppingListRepository).delete(shoppingList);
  }

  /**
   * Publica un evento {@code list.deleted} con el identificador, propietario y nombre de la lista.
   */
  @Test
  void delete_existing_publishesListDeletedEvent() {
    UUID publicId = UUID.randomUUID();
    ShoppingList shoppingList = list(1L, publicId, OWNER_ID, "A borrar");
    when(shoppingListRepository.findByPublicId(publicId)).thenReturn(Optional.of(shoppingList));

    shoppingListService.delete(publicId, OWNER_ID);

    ArgumentCaptor<DomainEvent<?>> captor = domainEventCaptor();
    verify(domainEventPublisher).publish(captor.capture());
    DomainEvent<?> event = captor.getValue();
    assertThat(event.eventType()).isEqualTo(EventType.LIST_DELETED);
    assertThat(event.occurredAt()).isEqualTo(FIXED_INSTANT);
    assertThat(event.correlationId()).isEqualTo(CORRELATION_ID);
    assertThat(event.payload())
        .isInstanceOfSatisfying(
            ListDeletedEvent.class,
            payload -> {
              assertThat(payload.listId()).isEqualTo(publicId);
              assertThat(payload.ownerId()).isEqualTo(OWNER_ID);
              assertThat(payload.name()).isEqualTo("A borrar");
            });
  }

  /** Traduce la ausencia de una lista en el borrado a {@code LIST_NOT_FOUND}. */
  @Test
  void delete_missing_throwsNotFound() {
    UUID publicId = UUID.randomUUID();
    when(shoppingListRepository.findByPublicId(publicId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> shoppingListService.delete(publicId, OWNER_ID))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.LIST_NOT_FOUND));
  }

  /** Rechaza el renombrado de una lista ajena con {@code OWNER_MISMATCH} sin persistir cambios. */
  @Test
  void update_withOtherOwner_throwsOwnerMismatchAndDoesNotSave() {
    UUID publicId = UUID.randomUUID();
    ShoppingList shoppingList = list(1L, publicId, OWNER_ID, "Nombre antiguo");
    when(shoppingListRepository.findByPublicId(publicId)).thenReturn(Optional.of(shoppingList));

    assertThatThrownBy(
            () ->
                shoppingListService.update(
                    publicId, new UpdateListRequest(OTHER_OWNER_ID, "Nombre nuevo")))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.OWNER_MISMATCH));

    assertThat(shoppingList.getName()).isEqualTo("Nombre antiguo");
    verify(shoppingListRepository, never()).save(any(ShoppingList.class));
  }

  /** Rechaza el borrado de una lista ajena con {@code OWNER_MISMATCH} sin eliminarla. */
  @Test
  void delete_withOtherOwner_throwsOwnerMismatchAndDoesNotDelete() {
    UUID publicId = UUID.randomUUID();
    ShoppingList shoppingList = list(1L, publicId, OWNER_ID, "Ajena");
    when(shoppingListRepository.findByPublicId(publicId)).thenReturn(Optional.of(shoppingList));

    assertThatThrownBy(() -> shoppingListService.delete(publicId, OTHER_OWNER_ID))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.OWNER_MISMATCH));

    verify(shoppingListRepository, never()).delete(any(ShoppingList.class));
  }

  @SuppressWarnings("unchecked")
  private static ArgumentCaptor<DomainEvent<?>> domainEventCaptor() {
    return ArgumentCaptor.forClass(DomainEvent.class);
  }

  private static ShoppingList list(Long id, UUID publicId, UUID ownerId, String name) {
    ShoppingList shoppingList = new ShoppingList();
    shoppingList.setId(id);
    shoppingList.setPublicId(publicId);
    shoppingList.setOwnerId(ownerId);
    shoppingList.setName(name);
    return shoppingList;
  }

  private static ListItem item(
      UUID publicId,
      UUID productId,
      ProductType productType,
      String displayName,
      boolean purchased) {
    ListItem item = new ListItem();
    item.setPublicId(publicId);
    item.setProductId(productId);
    item.setProductType(productType);
    item.setDisplayName(displayName);
    item.setPurchased(purchased);
    return item;
  }
}
