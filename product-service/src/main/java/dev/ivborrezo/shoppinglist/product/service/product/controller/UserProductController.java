package dev.ivborrezo.shoppinglist.product.service.product.controller;

import dev.ivborrezo.shoppinglist.product.service.common.dto.PagedResponse;
import dev.ivborrezo.shoppinglist.product.service.product.dto.UserProductResponseDto;
import dev.ivborrezo.shoppinglist.product.service.product.service.UserProductService;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Controller REST para la consulta de productos de usuario del catálogo personal. */
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
}
