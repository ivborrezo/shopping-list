package dev.ivborrezo.shoppinglist.product.service.category.controller;

import dev.ivborrezo.shoppinglist.product.service.category.dto.CategoryResponseDto;
import dev.ivborrezo.shoppinglist.product.service.category.service.CategoryService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Controller REST para la gestión de categorías del catálogo. */
@RestController
@RequestMapping("/categories")
public class CategoryController {

  private final CategoryService categoryService;

  /**
   * Inicializa el controller con el servicio de categorías.
   *
   * @param categoryService servicio de categorías desde el que se obtienen los datos a exponer
   */
  public CategoryController(CategoryService categoryService) {
    this.categoryService = categoryService;
  }

  /**
   * Lista las categorías activas del catálogo.
   *
   * @return lista de DTOs con las categorías activas; vacía si no hay ninguna
   */
  @GetMapping
  public List<CategoryResponseDto> list() {
    return categoryService.findActive();
  }
}
