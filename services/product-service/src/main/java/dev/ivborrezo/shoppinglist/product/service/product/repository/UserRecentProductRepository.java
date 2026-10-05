package dev.ivborrezo.shoppinglist.product.service.product.repository;

import dev.ivborrezo.shoppinglist.product.service.common.ProductType;
import dev.ivborrezo.shoppinglist.product.service.product.entity.UserRecentProduct;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repositorio JPA de la entidad {@link UserRecentProduct}. */
public interface UserRecentProductRepository extends JpaRepository<UserRecentProduct, Long> {

  /**
   * Busca el reciente del usuario para el producto indicado.
   *
   * @param userId identificador del usuario
   * @param productType tipo del producto ({@code BASE} o {@code USER})
   * @param productPublicId identificador público del producto
   * @return el reciente si existe, o vacío si no
   */
  Optional<UserRecentProduct> findByUserIdAndProductTypeAndProductPublicId(
      UUID userId, ProductType productType, UUID productPublicId);

  /**
   * Recupera los productos recientes de un usuario, ordenados de más reciente a más antiguo.
   *
   * @param userId identificador del usuario
   * @return lista con los diez productos recientes del usuario indicado
   */
  List<UserRecentProduct> findTop10ByUserIdOrderByLastUsedAtDesc(UUID userId);
}
