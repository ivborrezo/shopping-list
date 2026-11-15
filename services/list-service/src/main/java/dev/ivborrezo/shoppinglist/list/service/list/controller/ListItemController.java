package dev.ivborrezo.shoppinglist.list.service.list.controller;

import dev.ivborrezo.shoppinglist.list.service.common.security.CurrentUserId;
import dev.ivborrezo.shoppinglist.list.service.list.dto.AddListItemRequest;
import dev.ivborrezo.shoppinglist.list.service.list.dto.ListItemResponse;
import dev.ivborrezo.shoppinglist.list.service.list.dto.UpdateListItemPurchasedRequest;
import dev.ivborrezo.shoppinglist.list.service.list.service.ListItemService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Controller REST para la gestión de los ítems de una lista de la compra. */
@RestController
@RequestMapping("/lists/{id}/items")
public class ListItemController {

  private final ListItemService listItemService;

  /**
   * Construye el controller con el servicio de ítems.
   *
   * @param listItemService servicio de aplicación de ítems de lista
   */
  public ListItemController(ListItemService listItemService) {
    this.listItemService = listItemService;
  }

  /**
   * Añade un producto a la lista indicada resolviendo su nombre como snapshot.
   *
   * @param id identificador público de la lista
   * @param userId identificador del usuario autenticado
   * @param request petición con la referencia al producto, validada con Bean Validation
   * @return {@code 201 Created} con el ítem creado y la cabecera {@code Location}
   */
  @PostMapping
  public ResponseEntity<ListItemResponse> add(
      @PathVariable UUID id,
      @CurrentUserId UUID userId,
      @Valid @RequestBody AddListItemRequest request) {
    ListItemResponse created = listItemService.add(id, userId, request);
    URI location = URI.create("/lists/" + id + "/items/" + created.id());
    return ResponseEntity.created(location).body(created);
  }

  /**
   * Marca o desmarca como comprado un ítem de la lista.
   *
   * @param id identificador público de la lista
   * @param itemId identificador público del ítem
   * @param userId identificador del usuario autenticado
   * @param request petición con el nuevo estado de compra, validada con Bean Validation
   * @return el ítem actualizado
   */
  @PatchMapping("/{itemId}")
  public ListItemResponse updatePurchased(
      @PathVariable UUID id,
      @PathVariable UUID itemId,
      @CurrentUserId UUID userId,
      @Valid @RequestBody UpdateListItemPurchasedRequest request) {
    return listItemService.updatePurchased(id, itemId, userId, request);
  }

  /**
   * Elimina un ítem de la lista.
   *
   * @param id identificador público de la lista
   * @param itemId identificador público del ítem
   * @param userId identificador del usuario autenticado
   * @return {@code 204 No Content} si el borrado fue exitoso
   */
  @DeleteMapping("/{itemId}")
  public ResponseEntity<Void> remove(
      @PathVariable UUID id, @PathVariable UUID itemId, @CurrentUserId UUID userId) {
    listItemService.remove(id, itemId, userId);
    return ResponseEntity.noContent().build();
  }
}
