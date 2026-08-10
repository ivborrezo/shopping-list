package dev.ivborrezo.shoppinglist.product.service.product.repository;

import dev.ivborrezo.shoppinglist.product.service.product.entity.BaseProduct;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repositorio JPA de la entidad {@link BaseProduct}. */
public interface BaseProductRepository extends JpaRepository<BaseProduct, Long> {

  /**
   * Recupera los productos base activos, paginados.
   *
   * @param pageable parámetros de paginación (número de página, tamaño)
   * @return página de productos base activos
   */
  Page<BaseProduct> findByIsActiveTrue(Pageable pageable);

  /**
   * Recupera los productos base activos de una categoría concreta, paginados.
   *
   * @param categoryId identificador de la categoría por la que filtrar
   * @param pageable parámetros de paginación (número de página, tamaño)
   * @return página de productos base activos de la categoría indicada
   */
  Page<BaseProduct> findByIsActiveTrueAndCategoryId(Long categoryId, Pageable pageable);

  /**
   * Busca productos base activos cuyo nombre localizado coincida parcialmente con el texto dado,
   * sin distinguir mayúsculas/minúsculas, paginados. La búsqueda se realiza sobre todas las
   * traducciones, independientemente del locale.
   *
   * @param text término de búsqueda parcial sobre el nombre localizado
   * @param pageable parámetros de paginación (número de página, tamaño)
   * @return página de productos base activos cuyo nombre coincide con el texto
   */
  @Query(
      """
      SELECT DISTINCT bp FROM BaseProduct bp
      JOIN bp.translations t
      WHERE bp.isActive = true
        AND LOWER(t.name) LIKE LOWER(CONCAT('%', :text, '%'))
      """)
  Page<BaseProduct> findByIsActiveTrueAndText(@Param("text") String text, Pageable pageable);
}
