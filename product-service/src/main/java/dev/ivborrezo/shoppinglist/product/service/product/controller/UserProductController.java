package dev.ivborrezo.shoppinglist.product.service.product.controller;

import dev.ivborrezo.shoppinglist.product.service.common.dto.PagedResponse;
import dev.ivborrezo.shoppinglist.product.service.product.dto.CreateUserProductRequest;
import dev.ivborrezo.shoppinglist.product.service.product.dto.UpdateUserProductRequest;
import dev.ivborrezo.shoppinglist.product.service.product.dto.UserProductResponseDto;
import dev.ivborrezo.shoppinglist.product.service.product.service.UserProductService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Controller REST para la consulta y creación de productos de usuario del catálogo personal. */
@RestController
@RequestMapping("/user-products")
public class UserProductController {

  private final UserProductService userProductService;

  public UserProductController(UserProductService userProductService) {
    this.userProductService = userProductService;
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
  public PagedResponse<UserProductResponseDto> list(
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
  public UserProductResponseDto getById(@PathVariable Long id) {
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
  public UserProductResponseDto update(
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
  public ResponseEntity<UserProductResponseDto> create(
      @Valid @RequestBody CreateUserProductRequest request, Locale locale) {
    UserProductResponseDto created = userProductService.create(request, locale);
    URI location = URI.create("/user-products/" + created.id());
    return ResponseEntity.created(location).body(created);
  }
}
