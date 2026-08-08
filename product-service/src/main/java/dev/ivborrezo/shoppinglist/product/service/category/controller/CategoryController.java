package dev.ivborrezo.shoppinglist.product.service.category.controller;

import dev.ivborrezo.shoppinglist.product.service.category.dto.CategoryResponseDto;
import dev.ivborrezo.shoppinglist.product.service.category.service.CategoryService;
import java.util.List;
import java.util.Locale;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
   * Lista las categorías activas del catálogo con el nombre localizado al idioma de la cabecera
   * {@code Accept-Language}.
   *
   * @param locale idioma resuelto desde la cabecera por Spring MVC
   * @return lista de DTOs con las categorías activas; vacía si no hay ninguna
   */
  @GetMapping
  public List<CategoryResponseDto> list(Locale locale) {
    return categoryService.findActive(locale);
  }

  /**
   * Recupera una categoría por su identificador con el nombre localizado al idioma solicitado.
   *
   * @param id identificador de la categoría a recuperar
   * @param locale idioma resuelto desde la cabecera por Spring MVC
   * @return DTO de la categoría encontrada con su nombre localizado
   */
  @GetMapping("/{id}")
  public CategoryResponseDto getById(@PathVariable Long id, Locale locale) {
    return categoryService.findById(id, locale);
  }
}
