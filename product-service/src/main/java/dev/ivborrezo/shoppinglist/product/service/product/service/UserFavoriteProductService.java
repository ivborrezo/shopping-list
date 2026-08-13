package dev.ivborrezo.shoppinglist.product.service.product.service;

import dev.ivborrezo.shoppinglist.product.service.common.ProductType;
import dev.ivborrezo.shoppinglist.product.service.common.dto.PagedResponse;
import dev.ivborrezo.shoppinglist.product.service.product.dto.FavoriteToggleResponse;
import dev.ivborrezo.shoppinglist.product.service.product.dto.ProductReferenceDto;
import dev.ivborrezo.shoppinglist.product.service.product.entity.UserFavoriteProduct;
import dev.ivborrezo.shoppinglist.product.service.product.entity.UserProduct;
import dev.ivborrezo.shoppinglist.product.service.product.repository.BaseProductRepository;
import dev.ivborrezo.shoppinglist.product.service.product.repository.UserFavoriteProductRepository;
import dev.ivborrezo.shoppinglist.product.service.product.repository.UserProductRepository;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

  /**
   * Devuelve los favoritos del propietario paginados, ordenados de más reciente a más antiguo, con
   * el nombre del producto resuelto según su tipo.
   *
   * <p>Para productos base el nombre se resuelve con el fallback de localización del {@code
   * Accept-Language}; para productos de usuario se usa el nombre monolingüe almacenado. Si el
   * producto referenciado ya no existe, la referencia se conserva en el resultado con nombre {@code
   * null}.
   *
   * @param ownerId identificador del propietario de los favoritos
   * @param pageable parámetros de paginación (número de página, tamaño)
   * @param locale idioma en el que se resuelven los nombres de los productos base
   * @return página de DTOs con las referencias a los productos favoritos y sus nombres resueltos
   */
  public PagedResponse<ProductReferenceDto> findFavorites(
      UUID ownerId, Pageable pageable, Locale locale) {
    Page<UserFavoriteProduct> page =
        userFavoriteProductRepository.findByUserIdOrderByCreatedAtDesc(ownerId, pageable);
    List<ProductReferenceDto> dtos =
        page.getContent().stream()
            .map(favorite -> ProductReferenceDto.from(favorite, resolveName(favorite, locale)))
            .toList();
    return new PagedResponse<>(dtos, page.getNumber(), page.getSize(), page.getTotalElements());
  }

  /**
   * Resuelve el nombre mostrable del producto referenciado por el favorito según su tipo.
   *
   * @param favorite entidad de favorito de la que se lee la referencia al producto
   * @param locale idioma en el que se resuelve el nombre de los productos base
   * @return nombre localizado o monolingüe según el tipo; {@code null} si el producto ya no existe
   */
  private String resolveName(UserFavoriteProduct favorite, Locale locale) {
    if (favorite.getProductType() == ProductType.BASE) {
      return baseProductRepository
          .findById(favorite.getProductId())
          .map(base -> baseProductService.resolveName(base, locale))
          .orElse(null);
    }
    return userProductRepository
        .findById(favorite.getProductId())
        .map(UserProduct::getName)
        .orElse(null);
  }
}
