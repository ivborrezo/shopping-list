package dev.ivborrezo.shoppinglist.product.service.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.ivborrezo.shoppinglist.product.service.category.entity.Category;
import dev.ivborrezo.shoppinglist.product.service.category.repository.CategoryRepository;
import dev.ivborrezo.shoppinglist.product.service.common.BusinessException;
import dev.ivborrezo.shoppinglist.product.service.common.CaloriesPerEnum;
import dev.ivborrezo.shoppinglist.product.service.common.ErrorCode;
import dev.ivborrezo.shoppinglist.product.service.common.UnitEnum;
import dev.ivborrezo.shoppinglist.product.service.common.dto.PagedResponse;
import dev.ivborrezo.shoppinglist.product.service.product.dto.CreateUserProductRequest;
import dev.ivborrezo.shoppinglist.product.service.product.dto.UserProductResponse;
import dev.ivborrezo.shoppinglist.product.service.product.entity.BaseProduct;
import dev.ivborrezo.shoppinglist.product.service.product.entity.UserProduct;
import dev.ivborrezo.shoppinglist.product.service.product.repository.BaseProductRepository;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/**
 * Test unitario de {@link UserProductService}.
 *
 * <p>Ejercita el listado paginado de productos del propietario, con y sin filtro por categoría, la
 * recuperación por identificador público (cubriendo los {@code 404} por producto inexistente o
 * inactivo), la validación condicional de campos obligatorios en la creación y la resolución de los
 * identificadores públicos de categoría y producto base.
 */
@ExtendWith(MockitoExtension.class)
class UserProductServiceTest {

  private static final UUID OWNER_ID = UUID.fromString("aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee");

  private static final UUID PRODUCT_PUBLIC_ID =
      UUID.fromString("bbbbbbbb-cccc-4ddd-8eee-ffffffffffff");

  private static final UUID CATEGORY_PUBLIC_ID =
      UUID.fromString("cccccccc-dddd-4eee-8fff-111111111111");

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
    when(categoryRepository.findAllById(any()))
        .thenReturn(List.of(category(1L, CATEGORY_PUBLIC_ID)));

    PagedResponse<UserProductResponse> page =
        userProductService.findByOwner(OWNER_ID, PageRequest.of(0, 20));

    assertThat(page.content()).hasSize(1);
    assertThat(page.page()).isEqualTo(0);
    assertThat(page.totalElements()).isEqualTo(1);
    UserProductResponse dto = page.content().get(0);
    assertThat(dto.ownerId()).isEqualTo(OWNER_ID);
    assertThat(dto.name()).isEqualTo("Leche entera");
    assertThat(dto.categoryId()).isEqualTo(CATEGORY_PUBLIC_ID);
    assertThat(dto.defaultUnit()).isEqualTo(UnitEnum.UNIT);
    assertThat(dto.calories()).isEqualTo(150);
    assertThat(dto.caloriesPer()).isEqualTo(CaloriesPerEnum.G);
    assertThat(dto.isActive()).isTrue();
  }

  /** Filtra por categoría resolviendo el identificador interno y mapeando el público resultante. */
  @Test
  void findByOwner_withCategory_callsCategoryFilteredRepositoryQuery() {
    UUID categoryPublicId = UUID.randomUUID();
    Long internalCategoryId = 2L;
    UserProduct product = buildProduct(OWNER_ID, "Manzana", internalCategoryId, true);
    when(categoryRepository.findByPublicId(categoryPublicId))
        .thenReturn(Optional.of(category(internalCategoryId, categoryPublicId)));
    when(userProductRepository.findByOwnerIdAndIsActiveTrueAndCategoryId(
            eq(OWNER_ID), eq(internalCategoryId), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(product)));
    when(categoryRepository.findAllById(any()))
        .thenReturn(List.of(category(internalCategoryId, categoryPublicId)));

    PagedResponse<UserProductResponse> page =
        userProductService.findByOwner(OWNER_ID, PageRequest.of(0, 20), categoryPublicId);

    verify(userProductRepository)
        .findByOwnerIdAndIsActiveTrueAndCategoryId(
            eq(OWNER_ID), eq(internalCategoryId), any(Pageable.class));
    assertThat(page.content()).hasSize(1);
    assertThat(page.content().get(0).categoryId()).isEqualTo(categoryPublicId);
  }

  /** Devuelve una página vacía cuando la categoría del filtro no existe. */
  @Test
  void findByOwner_withUnknownCategory_returnsEmptyPage() {
    UUID unknownCategoryPublicId = UUID.randomUUID();
    when(categoryRepository.findByPublicId(unknownCategoryPublicId)).thenReturn(Optional.empty());

    PagedResponse<UserProductResponse> page =
        userProductService.findByOwner(OWNER_ID, PageRequest.of(2, 10), unknownCategoryPublicId);

    assertThat(page.content()).isEmpty();
    assertThat(page.page()).isEqualTo(2);
    assertThat(page.size()).isEqualTo(10);
    assertThat(page.totalElements()).isZero();
    verify(userProductRepository, never())
        .findByOwnerIdAndIsActiveTrueAndCategoryId(any(), any(), any());
  }

  /** Devuelve el DTO del producto activo existente con la categoría resuelta a su público. */
  @Test
  void findById_existingAndActive_returnsResponse() {
    UserProduct product = buildProduct(OWNER_ID, "Leche entera", 1L, true);
    product.setPublicId(PRODUCT_PUBLIC_ID);
    when(userProductRepository.findByPublicId(PRODUCT_PUBLIC_ID)).thenReturn(Optional.of(product));
    when(categoryRepository.findById(1L)).thenReturn(Optional.of(category(1L, CATEGORY_PUBLIC_ID)));

    UserProductResponse dto = userProductService.findById(PRODUCT_PUBLIC_ID);

    assertThat(dto.id()).isEqualTo(PRODUCT_PUBLIC_ID);
    assertThat(dto.categoryId()).isEqualTo(CATEGORY_PUBLIC_ID);
    assertThat(dto.ownerId()).isEqualTo(OWNER_ID);
    assertThat(dto.isActive()).isTrue();
  }

  /** Resuelve también el identificador público del producto base cuando la traza está presente. */
  @Test
  void findById_withBasedOnBaseId_resolvesBasePublicId() {
    UserProduct product = buildProduct(OWNER_ID, "Leche entera", 1L, true);
    product.setPublicId(PRODUCT_PUBLIC_ID);
    product.setBasedOnBaseId(5L);
    when(userProductRepository.findByPublicId(PRODUCT_PUBLIC_ID)).thenReturn(Optional.of(product));
    when(categoryRepository.findById(1L)).thenReturn(Optional.of(category(1L, CATEGORY_PUBLIC_ID)));
    UUID basePublicId = UUID.randomUUID();
    when(baseProductRepository.findById(5L)).thenReturn(Optional.of(baseProduct(5L, basePublicId)));

    UserProductResponse dto = userProductService.findById(PRODUCT_PUBLIC_ID);

    assertThat(dto.basedOnBaseId()).isEqualTo(basePublicId);
  }

  /** Lanza {@code 404} cuando el identificador público no corresponde a ningún producto. */
  @Test
  void findById_missing_throwsNotFound() {
    when(userProductRepository.findByPublicId(any())).thenReturn(Optional.empty());

    assertThatThrownBy(() -> userProductService.findById(PRODUCT_PUBLIC_ID))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.USER_PRODUCT_NOT_FOUND));
  }

  /** Lanza {@code 404} cuando el producto existe pero está inactivo. */
  @Test
  void findById_inactive_throwsNotFound() {
    UserProduct product = buildProduct(OWNER_ID, "Leche entera", 1L, false);
    product.setPublicId(PRODUCT_PUBLIC_ID);
    when(userProductRepository.findByPublicId(PRODUCT_PUBLIC_ID)).thenReturn(Optional.of(product));

    assertThatThrownBy(() -> userProductService.findById(PRODUCT_PUBLIC_ID))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.USER_PRODUCT_NOT_FOUND));
  }

  /**
   * Lanza {@code DEFAULT_UNIT_REQUIRED} al crear un producto sin {@code basedOnBaseId} que aporte
   * la unidad por defecto.
   */
  @Test
  void create_withoutBasedOnBaseIdAndWithoutDefaultUnit_throwsDefaultUnitRequired() {
    CreateUserProductRequest request =
        new CreateUserProductRequest(
            OWNER_ID, "Leche entera", null, null, null, null, 150, CaloriesPerEnum.G, null, null);

    assertThatThrownBy(() -> userProductService.create(request, Locale.ENGLISH))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.DEFAULT_UNIT_REQUIRED));
  }

  /**
   * Lanza {@code CALORIES_PER_REQUIRED} al crear un producto sin {@code basedOnBaseId} que aporte
   * las calorías por unidad.
   */
  @Test
  void create_withoutBasedOnBaseIdAndWithoutCaloriesPer_throwsCaloriesPerRequired() {
    CreateUserProductRequest request =
        new CreateUserProductRequest(
            OWNER_ID, "Leche entera", null, null, null, UnitEnum.UNIT, 150, null, null, null);

    assertThatThrownBy(() -> userProductService.create(request, Locale.ENGLISH))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.CALORIES_PER_REQUIRED));
  }

  /**
   * Copia de la base los campos ausentes, hereda su categoría y resuelve los identificadores
   * públicos para la respuesta.
   */
  @Test
  void create_withBasedOnBaseId_copiesFieldsFromBaseAndResolvesPublicIds() {
    UUID basePublicId = UUID.randomUUID();
    BaseProduct base = baseProduct(10L, basePublicId);
    base.setCategoryId(5L);
    base.setDefaultUnit(UnitEnum.UNIT);
    base.setCaloriesPer(CaloriesPerEnum.G);
    when(baseProductRepository.findByPublicId(basePublicId)).thenReturn(Optional.of(base));
    when(baseProductService.resolveName(base, Locale.ENGLISH)).thenReturn("Leche entera");
    when(categoryRepository.findById(5L)).thenReturn(Optional.of(category(5L, CATEGORY_PUBLIC_ID)));

    CreateUserProductRequest request =
        new CreateUserProductRequest(
            OWNER_ID, null, null, null, basePublicId, null, null, null, null, null);
    UserProduct saved = buildProduct(OWNER_ID, "Leche entera", 5L, true);
    saved.setBasedOnBaseId(10L);
    when(userProductRepository.save(any())).thenReturn(saved);

    UserProductResponse response = userProductService.create(request, Locale.ENGLISH);

    assertThat(response.name()).isEqualTo("Leche entera");
    assertThat(response.categoryId()).isEqualTo(CATEGORY_PUBLIC_ID);
    assertThat(response.basedOnBaseId()).isEqualTo(basePublicId);
  }

  /** Lanza {@code 400} cuando el producto base indicado no existe. */
  @Test
  void create_withUnknownBase_throwsInvalidBaseProduct() {
    UUID basePublicId = UUID.randomUUID();
    when(baseProductRepository.findByPublicId(basePublicId)).thenReturn(Optional.empty());

    CreateUserProductRequest request =
        new CreateUserProductRequest(
            OWNER_ID, null, null, null, basePublicId, null, null, null, null, null);

    assertThatThrownBy(() -> userProductService.create(request, Locale.ENGLISH))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_BASE_PRODUCT));
  }

  /**
   * Resuelve la categoría indicada en el body a su identificador interno y devuelve su público en
   * la respuesta.
   */
  @Test
  void create_withCategoryId_resolvesInternalCategoryAndReturnsPublicId() {
    when(categoryRepository.findByPublicId(CATEGORY_PUBLIC_ID))
        .thenReturn(Optional.of(category(3L, CATEGORY_PUBLIC_ID)));

    CreateUserProductRequest request =
        new CreateUserProductRequest(
            OWNER_ID,
            "Leche entera",
            null,
            CATEGORY_PUBLIC_ID,
            null,
            UnitEnum.UNIT,
            150,
            CaloriesPerEnum.G,
            null,
            null);
    UserProduct saved = buildProduct(OWNER_ID, "Leche entera", 3L, true);
    when(userProductRepository.save(any())).thenReturn(saved);

    UserProductResponse response = userProductService.create(request, Locale.ENGLISH);

    assertThat(response.categoryId()).isEqualTo(CATEGORY_PUBLIC_ID);
    assertThat(response.basedOnBaseId()).isNull();
  }

  /** Construye una categoría con los identificadores interno y público indicados. */
  private Category category(Long id, UUID publicId) {
    Category category = new Category();
    category.setId(id);
    category.setPublicId(publicId);
    return category;
  }

  /** Construye un producto base con los identificadores interno y público indicados. */
  private BaseProduct baseProduct(Long id, UUID publicId) {
    BaseProduct base = new BaseProduct();
    base.setId(id);
    base.setPublicId(publicId);
    return base;
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
