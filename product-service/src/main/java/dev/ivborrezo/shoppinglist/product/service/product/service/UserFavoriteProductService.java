package dev.ivborrezo.shoppinglist.product.service.product.service;

import dev.ivborrezo.shoppinglist.product.service.common.ProductType;
import dev.ivborrezo.shoppinglist.product.service.product.dto.FavoriteToggleResponse;
import dev.ivborrezo.shoppinglist.product.service.product.entity.UserFavoriteProduct;
import dev.ivborrezo.shoppinglist.product.service.product.repository.BaseProductRepository;
import dev.ivborrezo.shoppinglist.product.service.product.repository.UserFavoriteProductRepository;
import dev.ivborrezo.shoppinglist.product.service.product.repository.UserProductRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Servicio de gestión de los productos favoritos de un usuario. */
@Service
@Transactional(readOnly = true)
public class UserFavoriteProductService {

  private final UserFavoriteProductRepository userFavoriteProductRepository;

  private final UserRecentProductService userRecentProductService;

  private final BaseProductRepository baseProductRepository;

  private final UserProductRepository userProductRepository;

  private final BaseProductService baseProductService;

  /**
   * Construye el servicio de productos favoritos con el repositorio de favoritos, el servicio de
   * productos recientes para registrar la interacción, los repositorios de productos base y de
   * usuario para validar la existencia del producto, y el servicio de productos base.
   *
   * @param userFavoriteProductRepository repositorio de productos favoritos
   * @param userRecentProductService servicio de productos recientes
   * @param baseProductRepository repositorio de productos base
   * @param userProductRepository repositorio de productos de usuario
   * @param baseProductService servicio de productos base
   */
  public UserFavoriteProductService(
      UserFavoriteProductRepository userFavoriteProductRepository,
      UserRecentProductService userRecentProductService,
      BaseProductRepository baseProductRepository,
      UserProductRepository userProductRepository,
      BaseProductService baseProductService) {
    this.userFavoriteProductRepository = userFavoriteProductRepository;
    this.userRecentProductService = userRecentProductService;
    this.baseProductRepository = baseProductRepository;
    this.userProductRepository = userProductRepository;
    this.baseProductService = baseProductService;
  }

  /**
   * Marca o desmarca el producto indicado como favorito del propietario.
   *
   * <p>Si el producto ya estaba entre los favoritos del usuario, se elimina la relación y se
   * devuelve {@code favorited=false}; si no, se crea la relación, se registra la interacción en los
   * recientes del usuario y se devuelve {@code favorited=true}.
   *
   * @param ownerId identificador del propietario del favorito
   * @param productId identificador del producto a marcar, base o de usuario según {@code
   *     productType}
   * @param productTypeValue tipo de producto ({@code BASE} o {@code USER})
   * @return DTO con el estado del favorito tras la operación
   * @throws ResponseStatusException con {@code 400} si el tipo de producto no es válido
   * @throws ResponseStatusException con {@code 404} si el producto no existe según su tipo
   */
  @Transactional
  public FavoriteToggleResponse toggle(UUID ownerId, Long productId, String productTypeValue) {
    ProductType productType;
    try {
      productType = ProductType.valueOf(productTypeValue);
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid product type");
    }

    boolean exists =
        productType == ProductType.BASE
            ? baseProductRepository.existsById(productId)
            : userProductRepository.existsById(productId);
    if (!exists) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }

    if (userFavoriteProductRepository.existsByUserIdAndProductIdAndProductType(
        ownerId, productId, productType)) {
      userFavoriteProductRepository.deleteByUserIdAndProductIdAndProductType(
          ownerId, productId, productType);
      return new FavoriteToggleResponse(false);
    }

    UserFavoriteProduct favorite = new UserFavoriteProduct();
    favorite.setUserId(ownerId);
    favorite.setProductId(productId);
    favorite.setProductType(productType);
    favorite.setCreatedAt(Instant.now());
    userFavoriteProductRepository.save(favorite);
    userRecentProductService.markUsed(ownerId, productId, productType);
    return new FavoriteToggleResponse(true);
  }
}
