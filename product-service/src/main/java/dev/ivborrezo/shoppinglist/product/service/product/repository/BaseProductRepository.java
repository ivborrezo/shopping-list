package dev.ivborrezo.shoppinglist.product.service.product.repository;

import dev.ivborrezo.shoppinglist.product.service.product.entity.BaseProduct;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
