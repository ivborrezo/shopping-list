package dev.ivborrezo.shoppinglist.list.service.list.service;

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
import java.util.Objects;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Servicio de aplicación de los ítems de una lista de la compra.
 *
 * <p>Añade, marca o desmarca y elimina productos de una lista verificando la propiedad a través de
 * la lista padre. En el alta congela el snapshot {@code displayName} resuelto contra {@code
 * product-service} y delega la detección de duplicados en la constraint de unicidad de la base de
 * datos. Cada mutación marca la lista como modificada para refrescar su actividad y publica el
 * evento de dominio correspondiente a través de {@link DomainEventPublisher}, con el instante del
 * hecho capturado en el propio servicio.
 */
@Service
@Transactional(readOnly = true)
public class ListItemService {

  private final ShoppingListRepository shoppingListRepository;

  private final ListItemRepository listItemRepository;

  private final ProductCatalogClient productCatalogClient;

  private final Clock clock;

  private final DomainEventPublisher domainEventPublisher;

  /**
   * Inyecta los repositorios, el puerto de catálogo, la fuente de tiempo y el puerto de publicación
   * por constructor.
   *
   * @param shoppingListRepository repositorio de listas
   * @param listItemRepository repositorio de ítems de lista
   * @param productCatalogClient puerto de resolución de nombres de producto
   * @param clock fuente de tiempo para capturar el instante de los hechos
   * @param domainEventPublisher puerto de publicación de eventos de dominio
   */
  public ListItemService(
      ShoppingListRepository shoppingListRepository,
      ListItemRepository listItemRepository,
      ProductCatalogClient productCatalogClient,
      Clock clock,
      DomainEventPublisher domainEventPublisher) {
    this.shoppingListRepository = shoppingListRepository;
    this.listItemRepository = listItemRepository;
    this.productCatalogClient = productCatalogClient;
    this.clock = clock;
    this.domainEventPublisher = domainEventPublisher;
  }

  /**
   * Añade un producto a la lista resolviendo y congelando su nombre como snapshot.
   *
   * <p>La existencia de la lista se comprueba antes que la propiedad, y el tipo de producto se
   * valida antes de consultar el catálogo. La detección de duplicados se delega en la constraint de
   * unicidad de la base de datos.
   *
   * @param listPublicId identificador público de la lista
   * @param ownerId propietario que solicita el alta
   * @param request petición con la referencia al producto
   * @return el ítem creado, con el snapshot del nombre resuelto
   * @throws BusinessException con ErrorCode.LIST_NOT_FOUND si la lista no existe, ErrorCode
   *     OWNER_MISMATCH si {@code ownerId} no es su propietario, ErrorCode INVALID_PRODUCT_TYPE si
   *     el tipo no es válido, ErrorCode DUPLICATE_LIST_ITEM si el producto ya está en la lista, o
   *     los códigos del catálogo al resolver el producto
   */
  @Transactional
  public ListItemResponse add(UUID listPublicId, UUID ownerId, AddListItemRequest request) {
    ShoppingList list = findListOrThrow(listPublicId);
    if (!list.getOwnerId().equals(ownerId)) {
      throw new BusinessException(ErrorCode.OWNER_MISMATCH);
    }
    ProductType productType = parseProductType(request.productType());
    String displayName = productCatalogClient.resolveDisplayName(productType, request.productId());

    ListItem item = new ListItem();
    item.setListId(Objects.requireNonNull(list.getId()));
    item.setProductId(request.productId());
    item.setProductType(productType);
    item.setDisplayName(displayName);
    item.setPurchased(false);

    ListItem saved;
    try {
      saved = listItemRepository.saveAndFlush(item);
    } catch (DataIntegrityViolationException ex) {
      throw new BusinessException(ErrorCode.DUPLICATE_LIST_ITEM);
    }
    list.touch();
    publish(
        EventType.LIST_ITEM_ADDED,
        new ListItemAddedEvent(
            Objects.requireNonNull(list.getPublicId()),
            Objects.requireNonNull(saved.getPublicId()),
            saved.getProductType(),
            saved.getProductId(),
            saved.getDisplayName()));
    return ListItemResponse.from(saved);
  }

  /**
   * Marca o desmarca como comprado un ítem de la lista.
   *
   * <p>La existencia de la lista se comprueba antes que la propiedad, y la del ítem después, de
   * modo que la precedencia de errores es {@code LIST_NOT_FOUND}, {@code OWNER_MISMATCH} y {@code
   * LIST_ITEM_NOT_FOUND}.
   *
   * @param listPublicId identificador público de la lista
   * @param itemPublicId identificador público del ítem
   * @param ownerId propietario que solicita el cambio
   * @param request petición con el nuevo estado de compra
   * @return el ítem actualizado
   * @throws BusinessException con ErrorCode.LIST_NOT_FOUND si la lista no existe, ErrorCode
   *     OWNER_MISMATCH si {@code ownerId} no es su propietario, o ErrorCode LIST_ITEM_NOT_FOUND si
   *     el ítem no existe o no pertenece a la lista
   */
  @Transactional
  public ListItemResponse updatePurchased(
      UUID listPublicId, UUID itemPublicId, UUID ownerId, UpdateListItemPurchasedRequest request) {
    ShoppingList list = findListOrThrow(listPublicId);
    if (!list.getOwnerId().equals(ownerId)) {
      throw new BusinessException(ErrorCode.OWNER_MISMATCH);
    }
    ListItem item = findItemOrThrow(list, itemPublicId);
    item.setPurchased(request.purchased());
    ListItem saved = listItemRepository.save(item);
    list.touch();
    publish(
        EventType.LIST_ITEM_PURCHASED,
        new ListItemPurchasedEvent(
            Objects.requireNonNull(list.getPublicId()),
            Objects.requireNonNull(saved.getPublicId()),
            saved.getProductType(),
            saved.getProductId(),
            saved.getDisplayName(),
            saved.getPurchased()));
    return ListItemResponse.from(saved);
  }

  /**
   * Elimina un ítem de la lista.
   *
   * <p>La existencia de la lista se comprueba antes que la propiedad, y la del ítem después, de
   * modo que la precedencia de errores es {@code LIST_NOT_FOUND}, {@code OWNER_MISMATCH} y {@code
   * LIST_ITEM_NOT_FOUND}.
   *
   * @param listPublicId identificador público de la lista
   * @param itemPublicId identificador público del ítem
   * @param ownerId propietario que solicita el borrado
   * @throws BusinessException con ErrorCode.LIST_NOT_FOUND si la lista no existe, ErrorCode
   *     OWNER_MISMATCH si {@code ownerId} no es su propietario, o ErrorCode LIST_ITEM_NOT_FOUND si
   *     el ítem no existe o no pertenece a la lista
   */
  @Transactional
  public void remove(UUID listPublicId, UUID itemPublicId, UUID ownerId) {
    ShoppingList list = findListOrThrow(listPublicId);
    if (!list.getOwnerId().equals(ownerId)) {
      throw new BusinessException(ErrorCode.OWNER_MISMATCH);
    }
    ListItem item = findItemOrThrow(list, itemPublicId);
    listItemRepository.delete(item);
    list.touch();
    publish(
        EventType.LIST_ITEM_REMOVED,
        new ListItemRemovedEvent(
            Objects.requireNonNull(list.getPublicId()),
            Objects.requireNonNull(item.getPublicId()),
            item.getProductType(),
            item.getProductId(),
            item.getDisplayName()));
  }

  private ShoppingList findListOrThrow(UUID publicId) {
    return shoppingListRepository
        .findByPublicId(publicId)
        .orElseThrow(() -> new BusinessException(ErrorCode.LIST_NOT_FOUND));
  }

  private ListItem findItemOrThrow(ShoppingList list, UUID itemPublicId) {
    return listItemRepository
        .findByPublicId(itemPublicId)
        .filter(item -> item.getListId().equals(list.getId()))
        .orElseThrow(() -> new BusinessException(ErrorCode.LIST_ITEM_NOT_FOUND));
  }

  private void publish(EventType type, Object payload) {
    domainEventPublisher.publish(
        new DomainEvent<>(type, MDC.get("correlationId"), clock.instant(), payload));
  }

  private ProductType parseProductType(String raw) {
    try {
      return ProductType.valueOf(raw);
    } catch (IllegalArgumentException ex) {
      throw new BusinessException(ErrorCode.INVALID_PRODUCT_TYPE);
    }
  }
}
