package dev.ivborrezo.shoppinglist.product.service.product.repository;

import dev.ivborrezo.shoppinglist.product.service.product.entity.UserProduct;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repositorio JPA de la entidad {@link UserProduct}. */
public interface UserProductRepository extends JpaRepository<UserProduct, Long> {

  /**
   * Recupera los productos de un propietario que están activos, paginados.
   *
   * @param ownerId identificador del propietario de los productos
   * @param pageable parámetros de paginación (número de página, tamaño)
   * @return página de productos activos del propietario indicado
   */
  Page<UserProduct> findByOwnerIdAndIsActiveTrue(UUID ownerId, Pageable pageable);

  /**
   * Recupera los productos de un propietario que están activos y pertenecen a una categoría
   * concreta, paginados.
   *
   * @param ownerId identificador del propietario de los productos
   * @param categoryId identificador de la categoría por la que filtrar
   * @param pageable parámetros de paginación (número de página, tamaño)
   * @return página de productos activos del propietario y categoría indicados
   */
  Page<UserProduct> findByOwnerIdAndIsActiveTrueAndCategoryId(
      UUID ownerId, Long categoryId, Pageable pageable);
}
