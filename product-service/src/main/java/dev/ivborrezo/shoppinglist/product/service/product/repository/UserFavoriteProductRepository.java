package dev.ivborrezo.shoppinglist.product.service.product.repository;

import dev.ivborrezo.shoppinglist.product.service.common.ProductType;
import dev.ivborrezo.shoppinglist.product.service.product.entity.UserFavoriteProduct;
import dev.ivborrezo.shoppinglist.product.service.product.entity.UserFavoriteProductId;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repositorio JPA de la entidad {@link UserFavoriteProduct}. */
public interface UserFavoriteProductRepository
    extends JpaRepository<UserFavoriteProduct, UserFavoriteProductId> {

  /**
   * Comprueba si el usuario tiene marcado como favorito el producto indicado.
   *
   * @param userId identificador del usuario
   * @param productId identificador del producto
   * @param productType tipo del producto ({@code BASE} o {@code USER})
   * @return {@code true} si el producto está en los favoritos del usuario
   */
  boolean existsByUserIdAndProductIdAndProductType(
      UUID userId, Long productId, ProductType productType);

  /**
   * Recupera los favoritos de un usuario ordenados de más reciente a más antiguo, paginados.
   *
   * @param userId identificador del usuario
   * @param pageable parámetros de paginación (número de página, tamaño)
   * @return página de favoritos del usuario indicado
   */
  Page<UserFavoriteProduct> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

  /**
   * Elimina el favorito del usuario para el producto indicado mediante SQL directo, sin pasar por
   * el ciclo de vida de Hibernate. Evita el bug de {@code @IdClass} en mutaciones individuales de
   * entidades con clave compuesta.
   *
   * @param userId identificador del usuario
   * @param productId identificador del producto
   * @param productType tipo del producto ({@code BASE} o {@code USER})
   */
  @Modifying(flushAutomatically = true)
  @Query(
      "DELETE FROM UserFavoriteProduct f WHERE f.userId = :userId AND f.productId = :productId "
          + "AND f.productType = :productType")
  void deleteByUserIdAndProductIdAndProductType(
      @Param("userId") UUID userId,
      @Param("productId") Long productId,
      @Param("productType") ProductType productType);
}
