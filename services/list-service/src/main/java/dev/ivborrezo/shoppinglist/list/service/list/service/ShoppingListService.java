package dev.ivborrezo.shoppinglist.list.service.list.service;

import dev.ivborrezo.shoppinglist.list.service.common.BusinessException;
import dev.ivborrezo.shoppinglist.list.service.common.ErrorCode;
import dev.ivborrezo.shoppinglist.list.service.common.dto.PagedResponse;
import dev.ivborrezo.shoppinglist.list.service.list.dto.CreateListRequest;
import dev.ivborrezo.shoppinglist.list.service.list.dto.ShoppingListResponse;
import dev.ivborrezo.shoppinglist.list.service.list.dto.ShoppingListSummaryResponse;
import dev.ivborrezo.shoppinglist.list.service.list.dto.UpdateListRequest;
import dev.ivborrezo.shoppinglist.list.service.list.entity.ListItem;
import dev.ivborrezo.shoppinglist.list.service.list.entity.ShoppingList;
import dev.ivborrezo.shoppinglist.list.service.list.repository.ListItemRepository;
import dev.ivborrezo.shoppinglist.list.service.list.repository.ShoppingListRepository;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Servicio de aplicación de listas de la compra.
 *
 * <p>Orquesta la persistencia del agregado lista y sus ítems para las operaciones de crear, listar,
 * obtener, renombrar y eliminar. La verificación de propiedad y la publicación de eventos se
 * abordan en pasos posteriores: aquí {@code ownerId} se recibe por contrato, pero todavía no se
 * contrasta con el propietario del recurso.
 */
@Service
@Transactional(readOnly = true)
public class ShoppingListService {

  private final ShoppingListRepository shoppingListRepository;

  private final ListItemRepository listItemRepository;

  /**
   * Inyecta los repositorios de listas e ítems por constructor.
   *
   * @param shoppingListRepository repositorio de listas
   * @param listItemRepository repositorio de ítems de lista
   */
  public ShoppingListService(
      ShoppingListRepository shoppingListRepository, ListItemRepository listItemRepository) {
    this.shoppingListRepository = shoppingListRepository;
    this.listItemRepository = listItemRepository;
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
   * Renombra una lista si la petición incluye un nombre nuevo.
   *
   * @param publicId identificador público de la lista a actualizar
   * @param request petición de renombrado; {@code name} nulo conserva el nombre actual
   * @return detalle de la lista actualizada, con sus ítems
   * @throws BusinessException con ErrorCode.LIST_NOT_FOUND si la lista no existe
   */
  @Transactional
  public ShoppingListResponse update(UUID publicId, UpdateListRequest request) {
    ShoppingList list = findListOrThrow(publicId);
    if (request.name() != null) {
      list.setName(request.name());
    }
    ShoppingList saved = shoppingListRepository.save(list);
    List<ListItem> items =
        listItemRepository.findByListIdOrderByPurchasedAscIdAsc(
            Objects.requireNonNull(saved.getId()));
    return ShoppingListResponse.from(saved, items);
  }

  /**
   * Elimina una lista por su identificador público.
   *
   * @param publicId identificador público de la lista a eliminar
   * @param ownerId propietario que solicita la eliminación
   * @throws BusinessException con ErrorCode.LIST_NOT_FOUND si la lista no existe
   */
  @Transactional
  public void delete(UUID publicId, UUID ownerId) {
    ShoppingList list = findListOrThrow(publicId);
    shoppingListRepository.delete(list);
  }

  private ShoppingList findListOrThrow(UUID publicId) {
    return shoppingListRepository
        .findByPublicId(publicId)
        .orElseThrow(() -> new BusinessException(ErrorCode.LIST_NOT_FOUND));
  }
}
