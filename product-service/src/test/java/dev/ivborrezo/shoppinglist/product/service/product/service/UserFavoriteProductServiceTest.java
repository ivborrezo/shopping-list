package dev.ivborrezo.shoppinglist.product.service.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.ivborrezo.shoppinglist.product.service.common.ProductType;
import dev.ivborrezo.shoppinglist.product.service.common.dto.PagedResponse;
import dev.ivborrezo.shoppinglist.product.service.product.dto.FavoriteToggleResponse;
import dev.ivborrezo.shoppinglist.product.service.product.dto.ProductReference;
import dev.ivborrezo.shoppinglist.product.service.product.entity.BaseProduct;
import dev.ivborrezo.shoppinglist.product.service.product.entity.UserFavoriteProduct;
import dev.ivborrezo.shoppinglist.product.service.product.entity.UserProduct;
import dev.ivborrezo.shoppinglist.product.service.product.repository.BaseProductRepository;
import dev.ivborrezo.shoppinglist.product.service.product.repository.UserFavoriteProductRepository;
import dev.ivborrezo.shoppinglist.product.service.product.repository.UserProductRepository;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Test unitario de {@link UserFavoriteProductService}.
 *
 * <p>Ejercita el toggle de favorito sobre el contrato del servicio: la validación del {@code
 * productType}, la comprobación de existencia del producto según su tipo y el marcado y desmarcado
 * del favorito, con la actualización de recientes solo en el marcado.
 */
@ExtendWith(MockitoExtension.class)
class UserFavoriteProductServiceTest {

  private static final UUID OWNER_ID = UUID.fromString("aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee");

  @Mock private UserFavoriteProductRepository userFavoriteProductRepository;

  @Mock private UserRecentProductService userRecentProductService;

  @Mock private BaseProductRepository baseProductRepository;

  @Mock private UserProductRepository userProductRepository;

  @Mock private BaseProductService baseProductService;

  private UserFavoriteProductService userFavoriteProductService;

  /** Instancia el servicio bajo test con los repositorios y servicios mockeados. */
  @BeforeEach
  void setUp() {
    userFavoriteProductService =
        new UserFavoriteProductService(
            userFavoriteProductRepository,
            userRecentProductService,
            baseProductRepository,
            userProductRepository,
            baseProductService);
  }

  /**
   * Lanza {@code 400} cuando el {@code productType} no corresponde a ningún {@link ProductType},
   * sin consultar ningún repositorio.
   */
  @Test
  void toggle_withInvalidProductType_throwsBadRequest() {
    assertThatThrownBy(() -> userFavoriteProductService.toggle(OWNER_ID, 1L, "CATALOG"))
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));

    verifyNoInteractions(
        userFavoriteProductRepository, baseProductRepository, userProductRepository);
    verifyNoInteractions(userRecentProductService);
  }

  /** Lanza {@code 404} cuando el producto base indicado no existe. */
  @Test
  void toggle_whenBaseProductDoesNotExist_throwsNotFound() {
    when(baseProductRepository.existsById(1L)).thenReturn(false);

    assertThatThrownBy(() -> userFavoriteProductService.toggle(OWNER_ID, 1L, "BASE"))
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
  }

  /** Lanza {@code 404} cuando el producto de usuario indicado no existe. */
  @Test
  void toggle_whenUserProductDoesNotExist_throwsNotFound() {
    when(userProductRepository.existsById(5L)).thenReturn(false);

    assertThatThrownBy(() -> userFavoriteProductService.toggle(OWNER_ID, 5L, "USER"))
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
  }

  /**
   * Marca el producto como favorito cuando aún no lo estaba: persiste la relación, actualiza los
   * recientes y devuelve {@code favorited=true}.
   */
  @Test
  void toggle_whenNotAlreadyFavorite_createsFavoriteUpdatesRecentAndReturnsTrue() {
    when(baseProductRepository.existsById(1L)).thenReturn(true);
    when(userFavoriteProductRepository.existsByUserIdAndProductIdAndProductType(
            eq(OWNER_ID), eq(1L), eq(ProductType.BASE)))
        .thenReturn(false);

    FavoriteToggleResponse response = userFavoriteProductService.toggle(OWNER_ID, 1L, "BASE");

    assertThat(response.favorited()).isTrue();
    verify(userFavoriteProductRepository).save(any(UserFavoriteProduct.class));
    verify(userRecentProductService).markUsed(eq(OWNER_ID), eq(1L), eq(ProductType.BASE));
  }

  /**
   * Desmarca un producto ya favorito eliminando la relación sin tocar los recientes y devuelve
   * {@code favorited=false}.
   */
  @Test
  void toggle_whenAlreadyFavorite_deletesFavoriteAndReturnsFalse() {
    when(baseProductRepository.existsById(1L)).thenReturn(true);
    when(userFavoriteProductRepository.existsByUserIdAndProductIdAndProductType(
            eq(OWNER_ID), eq(1L), eq(ProductType.BASE)))
        .thenReturn(true);

    FavoriteToggleResponse response = userFavoriteProductService.toggle(OWNER_ID, 1L, "BASE");

    assertThat(response.favorited()).isFalse();
    verify(userFavoriteProductRepository)
        .deleteByUserIdAndProductIdAndProductType(OWNER_ID, 1L, ProductType.BASE);
    verify(userRecentProductService, never()).markUsed(any(), any(), any());
  }

  /**
   * Resuelve el nombre de un favorito de producto base en el idioma solicitado mediante el servicio
   * de productos base.
   */
  @Test
  void findFavorites_withBaseProduct_resolvesLocalizedName() {
    UserFavoriteProduct favorite = new UserFavoriteProduct();
    favorite.setUserId(OWNER_ID);
    favorite.setProductId(1L);
    favorite.setProductType(ProductType.BASE);
    when(userFavoriteProductRepository.findByUserIdOrderByCreatedAtDesc(
            eq(OWNER_ID), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(favorite)));

    BaseProduct base = new BaseProduct();
    when(baseProductRepository.findById(1L)).thenReturn(Optional.of(base));
    when(baseProductService.resolveName(base, Locale.ENGLISH)).thenReturn("Milk");

    PagedResponse<ProductReference> page =
        userFavoriteProductService.findFavorites(OWNER_ID, PageRequest.of(0, 20), Locale.ENGLISH);

    assertThat(page.content()).hasSize(1);
    assertThat(page.content().get(0).productId()).isEqualTo(1L);
    assertThat(page.content().get(0).productType()).isEqualTo(ProductType.BASE);
    assertThat(page.content().get(0).name()).isEqualTo("Milk");
  }

  /** Usa el nombre monolingüe del producto de usuario para un favorito de tipo {@code USER}. */
  @Test
  void findFavorites_withUserProduct_usesMonolingualName() {
    UserFavoriteProduct favorite = new UserFavoriteProduct();
    favorite.setUserId(OWNER_ID);
    favorite.setProductId(5L);
    favorite.setProductType(ProductType.USER);
    when(userFavoriteProductRepository.findByUserIdOrderByCreatedAtDesc(
            eq(OWNER_ID), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(favorite)));

    UserProduct userProduct = new UserProduct();
    userProduct.setName("Mi producto");
    when(userProductRepository.findById(5L)).thenReturn(Optional.of(userProduct));

    PagedResponse<ProductReference> page =
        userFavoriteProductService.findFavorites(OWNER_ID, PageRequest.of(0, 20), Locale.ENGLISH);

    assertThat(page.content()).hasSize(1);
    assertThat(page.content().get(0).name()).isEqualTo("Mi producto");
  }

  /**
   * Incluye la fila en el listado con {@code name} a {@code null} cuando el producto referenciado
   * ya no existe.
   */
  @Test
  void findFavorites_orphanedProduct_returnsNullName() {
    UserFavoriteProduct favorite = new UserFavoriteProduct();
    favorite.setUserId(OWNER_ID);
    favorite.setProductId(1L);
    favorite.setProductType(ProductType.BASE);
    when(userFavoriteProductRepository.findByUserIdOrderByCreatedAtDesc(
            eq(OWNER_ID), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(favorite)));
    when(baseProductRepository.findById(1L)).thenReturn(Optional.empty());

    PagedResponse<ProductReference> page =
        userFavoriteProductService.findFavorites(OWNER_ID, PageRequest.of(0, 20), Locale.ENGLISH);

    assertThat(page.content()).hasSize(1);
    assertThat(page.content().get(0).name()).isNull();
  }

  /** Devuelve una página vacía cuando el usuario no tiene ningún favorito. */
  @Test
  void findFavorites_empty_returnsEmptyPage() {
    when(userFavoriteProductRepository.findByUserIdOrderByCreatedAtDesc(
            eq(OWNER_ID), any(Pageable.class)))
        .thenReturn(Page.empty());

    PagedResponse<ProductReference> page =
        userFavoriteProductService.findFavorites(OWNER_ID, PageRequest.of(0, 20), Locale.ENGLISH);

    assertThat(page.content()).isEmpty();
    assertThat(page.totalElements()).isZero();
  }
}
