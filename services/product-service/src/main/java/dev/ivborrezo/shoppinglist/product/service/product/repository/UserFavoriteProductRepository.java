package dev.ivborrezo.shoppinglist.product.service.product.repository;

import dev.ivborrezo.shoppinglist.product.service.common.ProductType;
import dev.ivborrezo.shoppinglist.product.service.product.entity.UserFavoriteProduct;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repositorio JPA de la entidad {@link UserFavoriteProduct}. */
public interface UserFavoriteProductRepository extends JpaRepository<UserFavoriteProduct, Long> {

  /**
   * Comprueba si el usuario tiene marcado como favorito el producto indicado.
   *
   * @param userId identificador del usuario
   * @param productType tipo del producto ({@code BASE} o {@code USER})
   * @param productPublicId identificador público del producto
   * @return {@code true} si el producto está en los favoritos del usuario
   */
  boolean existsByUserIdAndProductTypeAndProductPublicId(
      UUID userId, ProductType productType, UUID productPublicId);

  /**
   * Recupera los favoritos de un usuario ordenados de más reciente a más antiguo, paginados.
   *
   * @param userId identificador del usuario
   * @param pageable parámetros de paginación (número de página, tamaño)
   * @return página de favoritos del usuario indicado
   */
  Page<UserFavoriteProduct> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

  /**
   * Elimina el favorito del usuario para el producto indicado.
   *
   * @param userId identificador del usuario
   * @param productType tipo del producto ({@code BASE} o {@code USER})
   * @param productPublicId identificador público del producto
   */
  void deleteByUserIdAndProductTypeAndProductPublicId(
      UUID userId, ProductType productType, UUID productPublicId);
}
