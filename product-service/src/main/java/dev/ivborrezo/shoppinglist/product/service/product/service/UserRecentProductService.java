package dev.ivborrezo.shoppinglist.product.service.product.service;

import dev.ivborrezo.shoppinglist.product.service.common.ProductType;
import dev.ivborrezo.shoppinglist.product.service.product.entity.UserRecentProduct;
import dev.ivborrezo.shoppinglist.product.service.product.repository.BaseProductRepository;
import dev.ivborrezo.shoppinglist.product.service.product.repository.UserProductRepository;
import dev.ivborrezo.shoppinglist.product.service.product.repository.UserRecentProductRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Servicio de gestión del ranking de productos recientes de un usuario. */
@Service
@Transactional(readOnly = true)
public class UserRecentProductService {

  private final UserRecentProductRepository userRecentProductRepository;

  private final BaseProductRepository baseProductRepository;

  private final UserProductRepository userProductRepository;

  private final BaseProductService baseProductService;

  /**
   * Construye el servicio de productos recientes con los repositorios de recientes, productos base
   * y productos de usuario, y el servicio de productos base para resolver la información de los
   * productos referenciados.
   *
   * @param userRecentProductRepository repositorio de productos recientes
   * @param baseProductRepository repositorio de productos base
   * @param userProductRepository repositorio de productos de usuario
   * @param baseProductService servicio de productos base
   */
  public UserRecentProductService(
      UserRecentProductRepository userRecentProductRepository,
      BaseProductRepository baseProductRepository,
      UserProductRepository userProductRepository,
      BaseProductService baseProductService) {
    this.userRecentProductRepository = userRecentProductRepository;
    this.baseProductRepository = baseProductRepository;
    this.userProductRepository = userProductRepository;
    this.baseProductService = baseProductService;
  }

  /**
   * Registra una interacción del usuario con el producto actualizando su posición en el ranking de
   * recientes.
   *
   * @param userId identificador del usuario que interactúa con el producto
   * @param productId identificador del producto con el que se interactúa
   * @param productType tipo del producto ({@code BASE} o {@code USER})
   */
  @Transactional
  void markUsed(UUID userId, Long productId, ProductType productType) {
    if (userRecentProductRepository.existsByUserIdAndProductIdAndProductType(
        userId, productId, productType)) {
      userRecentProductRepository.updateLastUsedAt(userId, productId, productType, Instant.now());
      return;
    }
    UserRecentProduct recent = new UserRecentProduct();
    recent.setUserId(userId);
    recent.setProductId(productId);
    recent.setProductType(productType);
    recent.setLastUsedAt(Instant.now());
    userRecentProductRepository.save(recent);
  }
}
