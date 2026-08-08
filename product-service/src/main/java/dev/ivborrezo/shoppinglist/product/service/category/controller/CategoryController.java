package dev.ivborrezo.shoppinglist.product.service.category.controller;

import dev.ivborrezo.shoppinglist.product.service.category.dto.CategoryResponseDto;
import dev.ivborrezo.shoppinglist.product.service.category.service.CategoryService;
import java.util.List;
import java.util.Locale;
import org.springframework.web.bind.annotation.GetMapping;
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
}
