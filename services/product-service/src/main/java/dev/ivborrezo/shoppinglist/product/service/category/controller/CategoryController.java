package dev.ivborrezo.shoppinglist.product.service.category.controller;

import dev.ivborrezo.shoppinglist.product.service.category.dto.CategoryResponse;
import dev.ivborrezo.shoppinglist.product.service.category.dto.CreateCategoryRequest;
import dev.ivborrezo.shoppinglist.product.service.category.service.CategoryService;
import dev.ivborrezo.shoppinglist.product.service.common.dto.PagedResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.Locale;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Controller REST para la gestión de categorías del catálogo con soporte multiidioma. */
@RestController
@RequestMapping("/categories")
public class CategoryController {

  private final CategoryService categoryService;

  public CategoryController(CategoryService categoryService) {
    this.categoryService = categoryService;
  }

  /**
   * Lista las categorías activas del catálogo paginadas, con el nombre localizado al idioma de la
   * cabecera {@code Accept-Language}.
   *
   * @param pageable parámetros de paginación inyectados por Spring a partir de {@code page} y
   *     {@code size}
   * @param locale idioma resuelto desde la cabecera por Spring MVC
   * @return página de DTOs con las categorías activas y sus nombres localizados
   */
  @GetMapping
  public PagedResponse<CategoryResponse> list(@PageableDefault Pageable pageable, Locale locale) {
    return categoryService.findActive(locale, pageable);
  }

  /**
   * Recupera una categoría por su identificador con el nombre localizado al idioma solicitado.
   *
   * @param id identificador de la categoría a recuperar
   * @param locale idioma resuelto desde la cabecera por Spring MVC
   * @return DTO de la categoría encontrada con su nombre localizado
   */
  @GetMapping("/{id}")
  public CategoryResponse getById(@PathVariable Long id, Locale locale) {
    return categoryService.findById(id, locale);
  }

  /**
   * Crea una categoría del catálogo con sus traducciones.
   *
   * @param request petición con código, estado activo y traducciones
   * @param locale idioma resuelto desde la cabecera por Spring MVC
   * @return {@code 201 Created} con DTO y cabecera {@code Location}
   */
  @PostMapping
  public ResponseEntity<CategoryResponse> create(
      @Valid @RequestBody CreateCategoryRequest request, Locale locale) {
    CategoryResponse created = categoryService.create(request, locale);
    URI location = URI.create("/categories/" + created.id());
    return ResponseEntity.created(location).body(created);
  }
}
