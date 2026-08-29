package dev.ivborrezo.shoppinglist.product.service.category.repository;

import dev.ivborrezo.shoppinglist.product.service.category.entity.Category;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repositorio JPA de la entidad {@link Category}. */
public interface CategoryRepository extends JpaRepository<Category, Long> {

  /**
   * Recupera las categorías del catálogo marcadas como activas, paginadas.
   *
   * @param pageable parámetros de paginación (número de página, tamaño)
   * @return página de categorías activas
   */
  Page<Category> findByIsActiveTrue(Pageable pageable);

  /**
   * Comprueba si existe una categoría con el código dado.
   *
   * @param code código de la categoría a comprobar
   * @return true si ya existe una categoría con ese código
   */
  boolean existsByCode(String code);
}
