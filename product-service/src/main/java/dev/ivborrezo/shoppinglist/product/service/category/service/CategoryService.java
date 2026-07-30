package dev.ivborrezo.shoppinglist.product.service.category.service;

import dev.ivborrezo.shoppinglist.product.service.category.dto.CategoryResponseDto;
import dev.ivborrezo.shoppinglist.product.service.category.repository.CategoryRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Servicio de gestión de categorías del catálogo. */
@Service
@Transactional(readOnly = true)
public class CategoryService {

  private final CategoryRepository categoryRepository;

  /** Inicializa el servicio con el repositorio de categorías e inyecta dependencias */
  public CategoryService(CategoryRepository categoryRepository) {
    this.categoryRepository = categoryRepository;
  }

  /**
   * Devuelve las categorías activas del catálogo, materializadas como DTOs de respuesta.
   *
   * @return lista de DTOs con las categorías activas; vacía si no hay ninguna
   */
  public List<CategoryResponseDto> findActive() {
    return categoryRepository.findByIsActiveTrue().stream().map(CategoryResponseDto::from).toList();
  }
}
