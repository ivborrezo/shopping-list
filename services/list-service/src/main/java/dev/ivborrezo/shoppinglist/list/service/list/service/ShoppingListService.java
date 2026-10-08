package dev.ivborrezo.shoppinglist.list.service.list.service;

import dev.ivborrezo.shoppinglist.list.service.common.BusinessException;
import dev.ivborrezo.shoppinglist.list.service.common.ErrorCode;
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
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Servicio de aplicación de listas de la compra.
 *
 * <p>Orquesta la persistencia del agregado lista y sus ítems para las operaciones de crear, listar,
 * obtener, renombrar y eliminar. La propiedad se verifica en las mutaciones (renombrar y eliminar),
 * que exigen que {@code ownerId} coincida con el propietario almacenado; las lecturas siguen siendo
 * abiertas. Cada mutación publica el evento de dominio correspondiente a través de {@link
 * DomainEventPublisher}, con el instante del hecho capturado en el propio servicio.
 */
@Service
@Transactional(readOnly = true)
public class ShoppingListService {

  private final ShoppingListRepository shoppingListRepository;

  private final ListItemRepository listItemRepository;

  private final Clock clock;

  private final DomainEventPublisher domainEventPublisher;

  /**
   * Inyecta los repositorios, la fuente de tiempo y el puerto de publicación por constructor.
   *
   * @param shoppingListRepository repositorio de listas
   * @param listItemRepository repositorio de ítems de lista
   * @param clock fuente de tiempo para capturar el instante de los hechos
   * @param domainEventPublisher puerto de publicación de eventos de dominio
   */
  public ShoppingListService(
      ShoppingListRepository shoppingListRepository,
      ListItemRepository listItemRepository,
      Clock clock,
      DomainEventPublisher domainEventPublisher) {
    this.shoppingListRepository = shoppingListRepository;
    this.listItemRepository = listItemRepository;
    this.clock = clock;
    this.domainEventPublisher = domainEventPublisher;
  }

  /**
   * Devuelve las listas de un propietario paginadas, como resúmenes sin ítems.
   *
   * @param ownerId identificador del propietario del que se listan las listas
   * @param pageable parámetros de paginación (número de página, tamaño)
   * @return página de resúmenes de listas ordenadas por actividad descendente; vacía si no hay
   */
  public PagedResponse<ShoppingListSummaryResponse> findPage(UUID ownerId, Pageable pageable) {
    Page<ShoppingList> page =
        shoppingListRepository.findByOwnerIdOrderByUpdatedAtDescIdDesc(ownerId, pageable);
    return PagedResponse.from(page.map(ShoppingListSummaryResponse::from));
  }

  /**
   * Crea una lista de la compra vacía.
   *
   * @param request petición con el propietario y el nombre de la lista
   * @return detalle de la lista recién creada, sin ítems
   */
  @Transactional
  public ShoppingListResponse create(CreateListRequest request) {
    ShoppingList list = new ShoppingList();
    list.setOwnerId(request.ownerId());
    list.setName(request.name());
    ShoppingList saved = shoppingListRepository.save(list);
    publish(
        EventType.LIST_CREATED,
        new ListCreatedEvent(
            Objects.requireNonNull(saved.getPublicId()), saved.getOwnerId(), saved.getName()));
    return ShoppingListResponse.from(saved, List.of());
  }

  /**
   * Obtiene el detalle de una lista por su identificador público, con sus ítems existentes.
   *
   * @param publicId identificador público de la lista
   * @return detalle de la lista con sus ítems
   * @throws BusinessException con ErrorCode.LIST_NOT_FOUND si la lista no existe
   */
  public ShoppingListResponse findById(UUID publicId) {
    ShoppingList list = findListOrThrow(publicId);
    List<ListItem> items =
        listItemRepository.findByListIdOrderByPurchasedAscIdAsc(
            Objects.requireNonNull(list.getId()));
    return ShoppingListResponse.from(list, items);
  }

  /**
   * Renombra una lista si el solicitante es su propietario y la petición incluye un nombre nuevo.
   *
   * <p>La existencia de la lista se comprueba antes que la propiedad, de modo que un recurso
   * inexistente devuelve {@code LIST_NOT_FOUND} con independencia del {@code ownerId} recibido.
   *
   * @param publicId identificador público de la lista a actualizar
   * @param request petición de renombrado con el solicitante; {@code name} nulo conserva el nombre
   *     actual
   * @return detalle de la lista actualizada, con sus ítems
   * @throws BusinessException con ErrorCode.LIST_NOT_FOUND si la lista no existe, o con ErrorCode
   *     OWNER_MISMATCH si {@code ownerId} no es el propietario de la lista
   */
  @Transactional
  public ShoppingListResponse update(UUID publicId, UpdateListRequest request) {
    ShoppingList list = findListOrThrow(publicId);
    if (!list.getOwnerId().equals(request.ownerId())) {
      throw new BusinessException(ErrorCode.OWNER_MISMATCH);
    }
    String oldName = list.getName();
    if (request.name() != null) {
      list.setName(request.name());
    }
    ShoppingList saved = shoppingListRepository.save(list);
    if (request.name() != null) {
      publish(
          EventType.LIST_RENAMED,
          new ListRenamedEvent(
              Objects.requireNonNull(saved.getPublicId()),
              saved.getOwnerId(),
              oldName,
              saved.getName()));
    }
    List<ListItem> items =
        listItemRepository.findByListIdOrderByPurchasedAscIdAsc(
            Objects.requireNonNull(saved.getId()));
    return ShoppingListResponse.from(saved, items);
  }

  /**
   * Elimina una lista por su identificador público si el solicitante es su propietario.
   *
   * <p>La existencia de la lista se comprueba antes que la propiedad, de modo que un recurso
   * inexistente devuelve {@code LIST_NOT_FOUND} con independencia del {@code ownerId} recibido.
   *
   * @param publicId identificador público de la lista a eliminar
   * @param ownerId propietario que solicita la eliminación
   * @throws BusinessException con ErrorCode.LIST_NOT_FOUND si la lista no existe, o con ErrorCode
   *     OWNER_MISMATCH si {@code ownerId} no es el propietario de la lista
   */
  @Transactional
  public void delete(UUID publicId, UUID ownerId) {
    ShoppingList list = findListOrThrow(publicId);
    if (!list.getOwnerId().equals(ownerId)) {
      throw new BusinessException(ErrorCode.OWNER_MISMATCH);
    }
    UUID listId = Objects.requireNonNull(list.getPublicId());
    UUID listOwnerId = list.getOwnerId();
    String name = list.getName();
    shoppingListRepository.delete(list);
    publish(EventType.LIST_DELETED, new ListDeletedEvent(listId, listOwnerId, name));
  }

  private ShoppingList findListOrThrow(UUID publicId) {
    return shoppingListRepository
        .findByPublicId(publicId)
        .orElseThrow(() -> new BusinessException(ErrorCode.LIST_NOT_FOUND));
  }

  private void publish(EventType type, Object payload) {
    domainEventPublisher.publish(
        new DomainEvent<>(type, MDC.get("correlationId"), clock.instant(), payload));
  }
}
