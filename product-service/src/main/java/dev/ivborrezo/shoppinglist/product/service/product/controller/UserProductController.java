package dev.ivborrezo.shoppinglist.product.service.product.controller;

import dev.ivborrezo.shoppinglist.product.service.common.dto.PagedResponse;
import dev.ivborrezo.shoppinglist.product.service.product.dto.CreateUserProductRequest;
import dev.ivborrezo.shoppinglist.product.service.product.dto.FavoriteToggleResponse;
import dev.ivborrezo.shoppinglist.product.service.product.dto.ProductReference;
import dev.ivborrezo.shoppinglist.product.service.product.dto.UpdateUserProductRequest;
import dev.ivborrezo.shoppinglist.product.service.product.dto.UserProductResponse;
import dev.ivborrezo.shoppinglist.product.service.product.service.UserFavoriteProductService;
import dev.ivborrezo.shoppinglist.product.service.product.service.UserProductService;
import dev.ivborrezo.shoppinglist.product.service.product.service.UserRecentProductService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.Locale;
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

/**
 * Controller REST para la consulta y creación de productos de usuario del catálogo personal y la
 * gestión de sus favoritos.
 */
@RestController
@RequestMapping("/user-products")
public class UserProductController {

  private final UserProductService userProductService;

  private final UserFavoriteProductService userFavoriteProductService;

  private final UserRecentProductService userRecentProductService;

  /**
   * Construye el controller con los servicios de productos de usuario, favoritos y recientes.
   *
   * @param userProductService servicio de productos de usuario
   * @param userFavoriteProductService servicio de productos favoritos
   * @param userRecentProductService servicio de productos recientes
   */
  public UserProductController(
      UserProductService userProductService,
      UserFavoriteProductService userFavoriteProductService,
      UserRecentProductService userRecentProductService) {
    this.userProductService = userProductService;
    this.userFavoriteProductService = userFavoriteProductService;
    this.userRecentProductService = userRecentProductService;
  }

  /**
   * Lista los productos activos de un propietario paginados, con filtro opcional por categoría.
   *
   * @param ownerId identificador del propietario de los productos
   * @param categoryId identificador de categoría para filtrar; opcional
   * @param pageable parámetros de paginación inyectados por Spring a partir de {@code page} y
   *     {@code size}
   * @return página de DTOs con los productos activos del propietario indicado
   */
  @GetMapping
  public PagedResponse<UserProductResponse> list(
      @RequestParam UUID ownerId,
      @RequestParam(required = false) Long categoryId,
      @PageableDefault Pageable pageable) {
    if (categoryId != null) {
      return userProductService.findByOwner(ownerId, pageable, categoryId);
    }
    return userProductService.findByOwner(ownerId, pageable);
  }

  /**
   * Recupera un producto de usuario por su identificador.
   *
   * @param id identificador del producto a recuperar
   * @return DTO del producto encontrado
   */
  @GetMapping("/{id}")
  public UserProductResponse getById(@PathVariable Long id) {
    return userProductService.findById(id);
  }

  /**
   * Edita parcialmente un producto de usuario por su identificador, aplicando solo los campos no
   * nulos del body.
   *
   * <p>El {@code ownerId} del body actúa como verificación de propiedad: si no coincide con el
   * propietario almacenado, la edición se rechaza con {@code 403}. El contenido de los productos de
   * usuario es monolingüe, por lo que el endpoint no resuelve textos localizados.
   *
   * @param id identificador del producto a editar
   * @param request petición con los campos a modificar, validada con Bean Validation
   * @return DTO del producto de usuario tras aplicar los cambios
   */
  @PatchMapping("/{id}")
  public UserProductResponse update(
      @PathVariable Long id, @Valid @RequestBody UpdateUserProductRequest request) {
    return userProductService.update(id, request);
  }

  /**
   * Crea un producto de usuario y devuelve el recurso creado con su cabecera {@code Location}.
   *
   * @param request petición con los datos del producto de usuario, validada con Bean Validation
   * @param locale idioma de la petición en el que se resuelven los campos copiados del producto
   *     base
   * @return respuesta {@code 201} con el DTO del producto creado y la cabecera {@code Location}
   */
  @PostMapping
  public ResponseEntity<UserProductResponse> create(
      @Valid @RequestBody CreateUserProductRequest request, Locale locale) {
    UserProductResponse created = userProductService.create(request, locale);
    URI location = URI.create("/user-products/" + created.id());
    return ResponseEntity.created(location).body(created);
  }

  /**
   * Elimina un producto de usuario tras verificar que el {@code ownerId} del query param coincide
   * con el propietario almacenado.
   *
   * @param id identificador del producto de usuario a eliminar
   * @param ownerId identificador del propietario que solicita el borrado; obligatorio
   * @return {@code 204 No Content} si el borrado fue exitoso
   */
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable Long id, @RequestParam UUID ownerId) {
    userProductService.delete(id, ownerId);
    return ResponseEntity.noContent().build();
  }

  /**
   * Marca o desmarca como favorito el producto indicado para el propietario.
   *
   * <p>Si el producto ya estaba entre los favoritos del usuario, se desmarca y se responde {@code
   * favorited: false}; si no, se marca, se actualiza el reciente del usuario-producto y se responde
   * {@code favorited: true}.
   *
   * @param id identificador del producto a marcar, base o de usuario según {@code productType}
   * @param ownerId identificador del propietario del favorito
   * @param productType tipo de producto ({@code BASE} o {@code USER})
   * @return DTO con el estado del favorito tras la operación
   */
  @PostMapping("/{id}/favorite")
  public FavoriteToggleResponse toggleFavorite(
      @PathVariable Long id, @RequestParam UUID ownerId, @RequestParam String productType) {
    return userFavoriteProductService.toggle(ownerId, id, productType);
  }

  /**
   * Lista los productos favoritos del propietario paginados, con el nombre del producto resuelto.
   *
   * @param ownerId identificador del propietario de los favoritos
   * @param pageable parámetros de paginación inyectados por Spring a partir de {@code page} y
   *     {@code size}
   * @param locale idioma resuelto desde la cabecera por Spring MVC
   * @return página de DTOs con las referencias a los productos favoritos del propietario
   */
  @GetMapping("/favorites")
  public PagedResponse<ProductReference> listFavorites(
      @RequestParam UUID ownerId, @PageableDefault Pageable pageable, Locale locale) {
    return userFavoriteProductService.findFavorites(ownerId, pageable, locale);
  }

  /**
   * Lista los diez productos más recientes del propietario, con el nombre del producto resuelto.
   *
   * @param ownerId identificador del propietario
   * @param locale idioma resuelto desde la cabecera por Spring MVC
   * @return lista con las referencias a los diez productos recientes del propietario
   */
  @GetMapping("/recents")
  public List<ProductReference> listRecents(@RequestParam UUID ownerId, Locale locale) {
    return userRecentProductService.findRecents(ownerId, locale);
  }
}
