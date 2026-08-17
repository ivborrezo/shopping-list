package dev.ivborrezo.shoppinglist.product.service.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.ivborrezo.shoppinglist.product.service.category.repository.CategoryRepository;
import dev.ivborrezo.shoppinglist.product.service.common.BusinessException;
import dev.ivborrezo.shoppinglist.product.service.common.CaloriesPerEnum;
import dev.ivborrezo.shoppinglist.product.service.common.ErrorCode;
import dev.ivborrezo.shoppinglist.product.service.common.UnitEnum;
import dev.ivborrezo.shoppinglist.product.service.common.dto.PagedResponse;
import dev.ivborrezo.shoppinglist.product.service.product.dto.UserProductResponse;
import dev.ivborrezo.shoppinglist.product.service.product.entity.UserProduct;
import dev.ivborrezo.shoppinglist.product.service.product.repository.BaseProductRepository;
import dev.ivborrezo.shoppinglist.product.service.product.repository.UserProductRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/**
 * Test unitario de {@link UserProductService}.
 *
 * <p>Ejercita el listado paginado de productos del propietario, con y sin filtro por categoría, y
 * la recuperación por identificador, cubriendo los {@code 404} por producto inexistente o inactivo.
 */
@ExtendWith(MockitoExtension.class)
class UserProductServiceTest {

  private static final UUID OWNER_ID = UUID.fromString("aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee");

  @Mock private UserProductRepository userProductRepository;

  @Mock private BaseProductRepository baseProductRepository;

  @Mock private BaseProductService baseProductService;

  @Mock private CategoryRepository categoryRepository;

  private UserProductService userProductService;

  /** Instancia el servicio bajo test con los repositorios y servicios mockeados. */
  @BeforeEach
  void setUp() {
    userProductService =
        new UserProductService(
            userProductRepository, baseProductRepository, baseProductService, categoryRepository);
  }

  /** Lista los productos del propietario mapeados a DTO cuando no se filtra por categoría. */
  @Test
  void findByOwner_withoutCategory_callsRepositoryAndReturnsMappedPage() {
    UserProduct product = buildProduct(OWNER_ID, "Leche entera", 1L, true);
    when(userProductRepository.findByOwnerIdAndIsActiveTrue(eq(OWNER_ID), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(product)));

    PagedResponse<UserProductResponse> page =
        userProductService.findByOwner(OWNER_ID, PageRequest.of(0, 20));

    assertThat(page.content()).hasSize(1);
    assertThat(page.page()).isEqualTo(0);
    assertThat(page.totalElements()).isEqualTo(1);
    UserProductResponse dto = page.content().get(0);
    assertThat(dto.ownerId()).isEqualTo(OWNER_ID);
    assertThat(dto.name()).isEqualTo("Leche entera");
    assertThat(dto.categoryId()).isEqualTo(1L);
    assertThat(dto.defaultUnit()).isEqualTo(UnitEnum.UNIT);
    assertThat(dto.calories()).isEqualTo(150);
    assertThat(dto.caloriesPer()).isEqualTo(CaloriesPerEnum.G);
    assertThat(dto.isActive()).isTrue();
  }

  /** Filtra por categoría usando la consulta del repositorio restringida a esa categoría. */
  @Test
  void findByOwner_withCategory_callsCategoryFilteredRepositoryQuery() {
    UserProduct product = buildProduct(OWNER_ID, "Manzana", 2L, true);
    when(userProductRepository.findByOwnerIdAndIsActiveTrueAndCategoryId(
            eq(OWNER_ID), eq(2L), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(product)));

    PagedResponse<UserProductResponse> page =
        userProductService.findByOwner(OWNER_ID, PageRequest.of(0, 20), 2L);

    verify(userProductRepository)
        .findByOwnerIdAndIsActiveTrueAndCategoryId(eq(OWNER_ID), eq(2L), any(Pageable.class));
    assertThat(page.content()).hasSize(1);
    assertThat(page.content().get(0).categoryId()).isEqualTo(2L);
  }

  /** Devuelve el DTO del producto activo existente, con {@code isActive} a {@code true}. */
  @Test
  void findById_existingAndActive_returnsResponse() {
    UserProduct product = buildProduct(OWNER_ID, "Leche entera", 1L, true);
    product.setId(1L);
    when(userProductRepository.findById(1L)).thenReturn(Optional.of(product));

    UserProductResponse dto = userProductService.findById(1L);

    assertThat(dto.id()).isEqualTo(1L);
    assertThat(dto.ownerId()).isEqualTo(OWNER_ID);
    assertThat(dto.isActive()).isTrue();
  }

  /** Lanza {@code 404} cuando el identificador no corresponde a ningún producto. */
  @Test
  void findById_missing_throwsNotFound() {
    when(userProductRepository.findById(1L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> userProductService.findById(1L))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.USER_PRODUCT_NOT_FOUND));
  }

  /** Lanza {@code 404} cuando el producto existe pero está inactivo. */
  @Test
  void findById_inactive_throwsNotFound() {
    UserProduct product = buildProduct(OWNER_ID, "Leche entera", 1L, false);
    product.setId(1L);
    when(userProductRepository.findById(1L)).thenReturn(Optional.of(product));

    assertThatThrownBy(() -> userProductService.findById(1L))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.USER_PRODUCT_NOT_FOUND));
  }

  /**
   * Construye un producto de usuario con los campos mínimos para los tests.
   *
   * @param ownerId propietario del producto
   * @param name nombre del producto
   * @param categoryId categoría del producto
   * @param active si el producto está activo
   * @return entidad {@link UserProduct} con los valores indicados
   */
  private UserProduct buildProduct(UUID ownerId, String name, Long categoryId, boolean active) {
    UserProduct product = new UserProduct();
    product.setOwnerId(ownerId);
    product.setName(name);
    product.setDescription("Descripción de prueba");
    product.setCategoryId(categoryId);
    product.setDefaultUnit(UnitEnum.UNIT);
    product.setCalories(150);
    product.setCaloriesPer(CaloriesPerEnum.G);
    product.setShareWithListMembers(false);
    product.setShareWithFriends(false);
    product.setIsActive(active);
    return product;
  }
}
