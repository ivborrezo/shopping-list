package dev.ivborrezo.shoppinglist.product.service.category.repository;

import dev.ivborrezo.shoppinglist.product.service.category.entity.Category;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repositorio JPA de la entidad {@link Category}. */
public interface CategoryRepository extends JpaRepository<Category, Long> {

  /**
   * Recupera las categorías del catálogo marcadas como activas.
   *
   * @return lista de categorías activas; vacía si no hay ninguna
   */
  List<Category> findByIsActiveTrue();

  /**
   * Recupera las categorías del catálogo marcadas como activas, paginadas.
   *
   * @param pageable parámetros de paginación (número de página, tamaño)
   * @return página de categorías activas
   */
  Page<Category> findByIsActiveTrue(Pageable pageable);
}
