package dev.ivborrezo.shoppinglist.product.service.product.controller;

import dev.ivborrezo.shoppinglist.product.service.common.dto.PagedResponse;
import dev.ivborrezo.shoppinglist.product.service.product.dto.BaseProductResponseDto;
import dev.ivborrezo.shoppinglist.product.service.product.dto.CreateBaseProductRequest;
import dev.ivborrezo.shoppinglist.product.service.product.dto.UpdateBaseProductRequest;
import dev.ivborrezo.shoppinglist.product.service.product.service.BaseProductService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.Locale;
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

/** Controller REST para la consulta de productos base del catálogo con soporte multiidioma. */
@RestController
@RequestMapping("/base-products")
public class BaseProductController {

  private final BaseProductService baseProductService;

  public BaseProductController(BaseProductService baseProductService) {
    this.baseProductService = baseProductService;
  }

  /**
   * Lista los productos base activos paginados, con filtros opcionales por categoría y búsqueda
   * textual, y textos localizados al idioma de la cabecera {@code Accept-Language}.
   *
   * @param categoryId identificador de categoría para filtrar; opcional
   * @param text término de búsqueda textual sobre el nombre localizado; opcional
   * @param pageable parámetros de paginación inyectados por Spring a partir de {@code page} y
   *     {@code size}
   * @param locale idioma resuelto desde la cabecera por Spring MVC
   * @return página de DTOs con los productos activos y sus textos localizados
   */
  @GetMapping
  public PagedResponse<BaseProductResponseDto> list(
      @RequestParam(required = false) Long categoryId,
      @RequestParam(required = false) String text,
      @PageableDefault Pageable pageable,
      Locale locale) {
    return baseProductService.findActive(locale, pageable, categoryId, text);
  }

  /**
   * Recupera un producto base por su identificador con nombre y descripción localizados al idioma
   * solicitado.
   *
   * @param id identificador del producto base a recuperar
   * @param locale idioma resuelto desde la cabecera por Spring MVC
   * @return DTO del producto encontrado con sus textos localizados
   */
  @GetMapping("/{id}")
  public BaseProductResponseDto getById(@PathVariable Long id, Locale locale) {
    return baseProductService.findById(id, locale);
  }

  /**
   * Crea un producto base del catálogo con sus traducciones.
   *
   * @param request petición con los datos del producto y sus traducciones, validada por Bean
   *     Validation
   * @param locale idioma resuelto desde la cabecera por Spring MVC
   * @return {@code 201 Created} con DTO y cabecera {@code Location}
   */
  @PostMapping
  public ResponseEntity<BaseProductResponseDto> create(
      @Valid @RequestBody CreateBaseProductRequest request, Locale locale) {
    BaseProductResponseDto created = baseProductService.create(request, locale);
    URI location = URI.create("/base-products/" + created.id());
    return ResponseEntity.created(location).body(created);
  }

  /**
   * Edita parcialmente un producto base del catálogo aplicando solo los campos enviados.
   *
   * @param id identificador del producto base a editar
   * @param request petición con los campos a modificar; solo los no nulos se aplican
   * @param locale idioma resuelto desde la cabecera por Spring MVC
   * @return DTO del producto base tras aplicar los cambios, con sus textos localizados
   */
  @PatchMapping("/{id}")
  public BaseProductResponseDto update(
      @PathVariable Long id, @Valid @RequestBody UpdateBaseProductRequest request, Locale locale) {
    return baseProductService.update(id, request, locale);
  }
}
