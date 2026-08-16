package dev.ivborrezo.shoppinglist.product.service.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.ivborrezo.shoppinglist.product.service.common.ProductType;
import dev.ivborrezo.shoppinglist.product.service.product.dto.ProductReference;
import dev.ivborrezo.shoppinglist.product.service.product.entity.BaseProduct;
import dev.ivborrezo.shoppinglist.product.service.product.entity.UserRecentProduct;
import dev.ivborrezo.shoppinglist.product.service.product.repository.BaseProductRepository;
import dev.ivborrezo.shoppinglist.product.service.product.repository.UserProductRepository;
import dev.ivborrezo.shoppinglist.product.service.product.repository.UserRecentProductRepository;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Test unitario de {@link UserRecentProductService}.
 *
 * <p>Ejercita el registro de interacciones con los productos del usuario y el listado de recientes:
 * el insert del reciente cuando no existía, la actualización de su última interacción cuando ya
 * existía y el mapeo de la lista recuperada del repositorio a referencias con el nombre resuelto.
 */
@ExtendWith(MockitoExtension.class)
class UserRecentProductServiceTest {

  private static final UUID OWNER_ID = UUID.fromString("aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee");

  @Mock private UserRecentProductRepository userRecentProductRepository;

  @Mock private BaseProductRepository baseProductRepository;

  @Mock private UserProductRepository userProductRepository;

  @Mock private BaseProductService baseProductService;

  private UserRecentProductService userRecentProductService;

  /** Instancia el servicio bajo test con los repositorios y servicios mockeados. */
  @BeforeEach
  void setUp() {
    userRecentProductService =
        new UserRecentProductService(
            userRecentProductRepository,
            baseProductRepository,
            userProductRepository,
            baseProductService);
  }

  /**
   * Inserta el producto en el ranking de recientes cuando el usuario aún no lo tenía registrado.
   */
  @Test
  void markUsed_whenNotExists_insertsRecent() {
    when(userRecentProductRepository.existsByUserIdAndProductIdAndProductType(
            OWNER_ID, 1L, ProductType.BASE))
        .thenReturn(false);

    userRecentProductService.markUsed(OWNER_ID, 1L, ProductType.BASE);

    verify(userRecentProductRepository).save(any(UserRecentProduct.class));
    verify(userRecentProductRepository, never()).updateLastUsedAt(any(), any(), any(), any());
  }

  /**
   * Actualiza la última interacción del producto cuando el usuario ya lo tenía registrado entre sus
   * recientes.
   */
  @Test
  void markUsed_whenExists_updatesLastUsedAt() {
    when(userRecentProductRepository.existsByUserIdAndProductIdAndProductType(
            OWNER_ID, 1L, ProductType.BASE))
        .thenReturn(true);

    userRecentProductService.markUsed(OWNER_ID, 1L, ProductType.BASE);

    verify(userRecentProductRepository)
        .updateLastUsedAt(eq(OWNER_ID), eq(1L), eq(ProductType.BASE), any(Instant.class));
    verify(userRecentProductRepository, never()).save(any());
  }

  /**
   * Devuelve los recientes recuperados del repositorio como referencias con el nombre resuelto,
   * conservando el orden de más reciente a más antiguo.
   */
  @Test
  void findRecents_returnsTop10MappedWithResolvedNames() {
    UserRecentProduct recent3 = buildRecent(3L);
    UserRecentProduct recent1 = buildRecent(1L);
    when(userRecentProductRepository.findTop10ByUserIdOrderByLastUsedAtDesc(OWNER_ID))
        .thenReturn(List.of(recent3, recent1));

    BaseProduct base3 = new BaseProduct();
    BaseProduct base1 = new BaseProduct();
    when(baseProductRepository.findById(3L)).thenReturn(Optional.of(base3));
    when(baseProductRepository.findById(1L)).thenReturn(Optional.of(base1));
    when(baseProductService.resolveName(any(), any())).thenReturn("Leche entera");

    List<ProductReference> recents = userRecentProductService.findRecents(OWNER_ID, Locale.ENGLISH);

    assertThat(recents).hasSize(2);
    assertThat(recents).extracting(ProductReference::productId).containsExactly(3L, 1L);
    assertThat(recents.get(0).name()).isEqualTo("Leche entera");
    assertThat(recents.get(0).productType()).isEqualTo(ProductType.BASE);
  }

  /** Devuelve una lista vacía cuando el usuario no tiene ninguna interacción registrada. */
  @Test
  void findRecents_empty_returnsEmptyList() {
    when(userRecentProductRepository.findTop10ByUserIdOrderByLastUsedAtDesc(OWNER_ID))
        .thenReturn(List.of());

    List<ProductReference> recents = userRecentProductService.findRecents(OWNER_ID, Locale.ENGLISH);

    assertThat(recents).isEmpty();
  }

  /**
   * Conserva la referencia en el listado con {@code name} a {@code null} cuando el producto
   * referenciado ya no existe.
   */
  @Test
  void findRecents_orphanedProduct_returnsNullName() {
    UserRecentProduct recent = buildRecent(1L);
    when(userRecentProductRepository.findTop10ByUserIdOrderByLastUsedAtDesc(OWNER_ID))
        .thenReturn(List.of(recent));
    when(baseProductRepository.findById(1L)).thenReturn(Optional.empty());

    List<ProductReference> recents = userRecentProductService.findRecents(OWNER_ID, Locale.ENGLISH);

    assertThat(recents).hasSize(1);
    assertThat(recents.get(0).productId()).isEqualTo(1L);
    assertThat(recents.get(0).name()).isNull();
  }

  /**
   * Construye un reciente de producto base con la referencia indicada para los tests.
   *
   * @param productId identificador del producto base referenciado
   * @return entidad {@link UserRecentProduct} con la referencia indicada
   */
  private UserRecentProduct buildRecent(Long productId) {
    UserRecentProduct recent = new UserRecentProduct();
    recent.setUserId(OWNER_ID);
    recent.setProductId(productId);
    recent.setProductType(ProductType.BASE);
    return recent;
  }
}
