package dev.ivborrezo.shoppinglist.list.service.list.controller;

import dev.ivborrezo.shoppinglist.list.service.common.dto.PagedResponse;
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
import org.springframework.web.bind.annotation.RequestParam;
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
   * Lista las listas de un propietario paginadas, como resúmenes sin ítems.
   *
   * @param ownerId identificador del propietario del que se listan las listas
   * @param pageable parámetros de paginación inyectados por Spring a partir de {@code page} y
   *     {@code size}
   * @return página de resúmenes de listas del propietario
   */
  @GetMapping
  public PagedResponse<ShoppingListSummaryResponse> list(
      @RequestParam UUID ownerId, @PageableDefault(size = 20) Pageable pageable) {
    return shoppingListService.findPage(ownerId, pageable);
  }

  /**
   * Crea una lista de la compra vacía.
   *
   * @param request petición con el propietario y el nombre de la lista
   * @return {@code 201 Created} con el detalle de la lista creada y la cabecera {@code Location}
   */
  @PostMapping
  public ResponseEntity<ShoppingListResponse> create(
      @Valid @RequestBody CreateListRequest request) {
    ShoppingListResponse created = shoppingListService.create(request);
    URI location = URI.create("/lists/" + created.id());
    return ResponseEntity.created(location).body(created);
  }

  /**
   * Recupera el detalle de una lista por su identificador, con sus ítems.
   *
   * @param id identificador público de la lista
   * @return detalle de la lista encontrada
   */
  @GetMapping("/{id}")
  public ShoppingListResponse getById(@PathVariable UUID id) {
    return shoppingListService.findById(id);
  }

  /**
   * Renombra una lista si la petición incluye un nombre nuevo.
   *
   * @param id identificador público de la lista a actualizar
   * @param request petición con el nombre nuevo, validada con Bean Validation
   * @return detalle de la lista actualizada, con sus ítems
   */
  @PatchMapping("/{id}")
  public ShoppingListResponse update(
      @PathVariable UUID id, @Valid @RequestBody UpdateListRequest request) {
    return shoppingListService.update(id, request);
  }

  /**
   * Elimina una lista por su identificador.
   *
   * @param id identificador público de la lista a eliminar
   * @param ownerId identificador del propietario que solicita el borrado
   * @return {@code 204 No Content} si el borrado fue exitoso
   */
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable UUID id, @RequestParam UUID ownerId) {
    shoppingListService.delete(id, ownerId);
    return ResponseEntity.noContent().build();
  }
}
