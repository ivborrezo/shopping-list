package dev.ivborrezo.shoppinglist.product.service.product.repository;

import dev.ivborrezo.shoppinglist.product.service.common.ProductType;
import dev.ivborrezo.shoppinglist.product.service.product.entity.UserRecentProduct;
import dev.ivborrezo.shoppinglist.product.service.product.entity.UserRecentProductId;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repositorio JPA de la entidad {@link UserRecentProduct}. */
public interface UserRecentProductRepository
    extends JpaRepository<UserRecentProduct, UserRecentProductId> {

  /**
   * Comprueba si el usuario tiene registrado el producto indicado entre sus recientes.
   *
   * @param userId identificador del usuario
   * @param productId identificador del producto
   * @param productType tipo del producto ({@code BASE} o {@code USER})
   * @return {@code true} si el producto está entre los recientes del usuario
   */
  boolean existsByUserIdAndProductIdAndProductType(
      UUID userId, Long productId, ProductType productType);

  /**
   * Actualiza la fecha de la última interacción del producto indicado para el usuario mediante SQL
   * directo, sin pasar por el ciclo de vida de Hibernate. Evita el bug de {@code @IdClass} en
   * mutaciones individuales de entidades con clave compuesta.
   *
   * @param userId identificador del usuario
   * @param productId identificador del producto
   * @param productType tipo del producto ({@code BASE} o {@code USER})
   * @param lastUsedAt fecha de la última interacción
   * @return número de filas actualizadas
   */
  @Modifying(flushAutomatically = true)
  @Query(
      "UPDATE UserRecentProduct r SET r.lastUsedAt = :lastUsedAt WHERE r.userId = :userId "
          + "AND r.productId = :productId AND r.productType = :productType")
  int updateLastUsedAt(
      @Param("userId") UUID userId,
      @Param("productId") Long productId,
      @Param("productType") ProductType productType,
      @Param("lastUsedAt") Instant lastUsedAt);

  /**
   * Recupera los productos recientes de un usuario, ordenados de más reciente a más antiguo.
   *
   * @param userId identificador del usuario
   * @return lista con los diez productos recientes del usuario indicado
   */
  List<UserRecentProduct> findTop10ByUserIdOrderByLastUsedAtDesc(UUID userId);
}
