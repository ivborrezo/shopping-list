package dev.ivborrezo.shoppinglist.product.service.product.repository;

import dev.ivborrezo.shoppinglist.product.service.product.entity.BaseProductTranslation;
import dev.ivborrezo.shoppinglist.product.service.product.entity.BaseProductTranslationId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repositorio JPA de la entidad {@link BaseProductTranslation}. */
public interface BaseProductTranslationRepository
    extends JpaRepository<BaseProductTranslation, BaseProductTranslationId> {

  /**
   * Elimina todas las traducciones del producto base indicado mediante SQL directo, sin pasar por
   * el ciclo de vida de Hibernate. Evita el bug de {@code @IdClass} + {@code @MapsId} en mutaciones
   * individuales de entidades con clave compuesta.
   *
   * @param productId identificador del producto base cuyas traducciones se eliminan
   */
  @Modifying(flushAutomatically = true)
  @Query("DELETE FROM BaseProductTranslation bt WHERE bt.baseProduct.id = :productId")
  void deleteAllByProductId(@Param("productId") Long productId);
}
