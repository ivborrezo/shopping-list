package dev.ivborrezo.shoppinglist.list.service.list.controller;

import dev.ivborrezo.shoppinglist.list.service.common.dto.PagedResponse;
import dev.ivborrezo.shoppinglist.list.service.common.security.CurrentUserId;
import dev.ivborrezo.shoppinglist.list.service.list.dto.CreateListRequest;
import dev.ivborrezo.shoppinglist.list.service.list.dto.ShoppingListResponse;
import dev.ivborrezo.shoppinglist.list.service.list.dto.ShoppingListSummaryResponse;
import dev.ivborrezo.shoppinglist.list.service.list.dto.UpdateListRequest;
import dev.ivborrezo.shoppinglist.list.service.list.service.ShoppingListService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Controller REST para la gestión de listas de la compra. */
@RestController
@RequestMapping("/lists")
public class ListController {

  private final ShoppingListService shoppingListService;

  /**
   * Construye el controller con el servicio de listas.
   *
   * @param shoppingListService servicio de aplicación de listas
   */
  public ListController(ShoppingListService shoppingListService) {
    this.shoppingListService = shoppingListService;
  }

  /**
   * Lista las listas del usuario autenticado paginadas, como resúmenes sin ítems.
   *
   * @param userId identificador del usuario autenticado
   * @param pageable parámetros de paginación inyectados por Spring a partir de {@code page} y
   *     {@code size}
   * @return página de resúmenes de listas del usuario
   */
  @GetMapping
  public PagedResponse<ShoppingListSummaryResponse> list(
      @CurrentUserId UUID userId, @PageableDefault(size = 20) Pageable pageable) {
    return shoppingListService.findPage(userId, pageable);
  }

  /**
   * Crea una lista de la compra vacía para el usuario autenticado.
   *
   * @param userId identificador del usuario autenticado
   * @param request petición con el nombre de la lista
   * @return {@code 201 Created} con el detalle de la lista creada y la cabecera {@code Location}
   */
  @PostMapping
  public ResponseEntity<ShoppingListResponse> create(
      @CurrentUserId UUID userId, @Valid @RequestBody CreateListRequest request) {
    ShoppingListResponse created = shoppingListService.create(userId, request);
    URI location = URI.create("/lists/" + created.id());
    return ResponseEntity.created(location).body(created);
  }

  /**
   * Recupera el detalle de una lista propia por su identificador, con sus ítems.
   *
   * @param id identificador público de la lista
   * @param userId identificador del usuario autenticado
   * @return detalle de la lista encontrada
   */
  @GetMapping("/{id}")
  public ShoppingListResponse getById(@PathVariable UUID id, @CurrentUserId UUID userId) {
    return shoppingListService.findById(id, userId);
  }

  /**
   * Renombra una lista propia si la petición incluye un nombre nuevo.
   *
   * @param id identificador público de la lista a actualizar
   * @param userId identificador del usuario autenticado
   * @param request petición con el nombre nuevo, validada con Bean Validation
   * @return detalle de la lista actualizada, con sus ítems
   */
  @PatchMapping("/{id}")
  public ShoppingListResponse update(
      @PathVariable UUID id,
      @CurrentUserId UUID userId,
      @Valid @RequestBody UpdateListRequest request) {
    return shoppingListService.update(id, userId, request);
  }

  /**
   * Elimina una lista propia por su identificador.
   *
   * @param id identificador público de la lista a eliminar
   * @param userId identificador del usuario autenticado
   * @return {@code 204 No Content} si el borrado fue exitoso
   */
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable UUID id, @CurrentUserId UUID userId) {
    shoppingListService.delete(id, userId);
    return ResponseEntity.noContent().build();
  }
}
