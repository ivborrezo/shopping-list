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
import dev.ivborrezo.shoppinglist.product.service.product.dto.BaseProductResponse;
import dev.ivborrezo.shoppinglist.product.service.product.dto.CreateBaseProductRequest;
import dev.ivborrezo.shoppinglist.product.service.product.dto.UpdateBaseProductRequest;
import dev.ivborrezo.shoppinglist.product.service.product.entity.BaseProduct;
import dev.ivborrezo.shoppinglist.product.service.product.entity.BaseProductTranslation;
import dev.ivborrezo.shoppinglist.product.service.product.repository.BaseProductRepository;
import dev.ivborrezo.shoppinglist.product.service.product.repository.BaseProductTranslationRepository;
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

/**
 * Test unitario de {@link BaseProductService}.
 *
 * <p>Ejercita la resolución de nombres y descripciones localizados en {@code findActive} y {@code
 * findById}, los filtros por categoría y búsqueda textual del listado, y el ciclo de vida de
 * creación, edición y borrado con resolución del identificador público de la categoría.
 */
@ExtendWith(MockitoExtension.class)
class BaseProductServiceTest {

  private static final UUID CATEGORY_PUBLIC_ID =
      UUID.fromString("cccccccc-dddd-4eee-8fff-111111111111");

  @Mock private BaseProductRepository baseProductRepository;

  @Mock private CategoryRepository categoryRepository;

  @Mock private BaseProductTranslationRepository translationRepository;

  private BaseProductService baseProductService;

  /** Instancia el servicio bajo test con los repositorios mockeados. */
  @BeforeEach
  void setUp() {
    baseProductService =
        new BaseProductService(baseProductRepository, categoryRepository, translationRepository);
  }

  /** Devuelve el nombre en el idioma solicitado cuando existe traducción. */
  @Test
  void findActive_returnsNameInRequestedLocale() {
    ProductFixture milk =
        new ProductFixture(
            "whole_milk",
            1L,
            "L",
            null,
            "ML",
            new TranslationFixture("es", "Leche entera", "Descripción en español"),
            new TranslationFixture("en", "Whole milk", "Description in English"),
            new TranslationFixture("eu", "Esne osoa", "Deskribapena euskaraz"));
    when(baseProductRepository.findByIsActiveTrue(any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(milk)));
    when(categoryRepository.findAllById(any()))
        .thenReturn(List.of(category(1L, CATEGORY_PUBLIC_ID)));

    PagedResponse<BaseProductResponse> page =
        baseProductService.findActive(Locale.forLanguageTag("eu"), PageRequest.of(0, 20));

    assertThat(page.content()).hasSize(1);
    assertThat(page.content().get(0).name()).isEqualTo("Esne osoa");
    assertThat(page.content().get(0).categoryId()).isEqualTo(CATEGORY_PUBLIC_ID);
  }

  /** Aplica fallback a inglés cuando el idioma solicitado no tiene traducción. */
  @Test
  void findActive_fallsBackToEnglish_whenRequestedLocaleUnavailable() {
    ProductFixture milk =
        new ProductFixture(
            "whole_milk",
            1L,
            "L",
            null,
            "ML",
            new TranslationFixture("es", "Leche entera", null),
            new TranslationFixture("en", "Whole milk", null));
    when(baseProductRepository.findByIsActiveTrue(any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(milk)));
    when(categoryRepository.findAllById(any()))
        .thenReturn(List.of(category(1L, CATEGORY_PUBLIC_ID)));

    PagedResponse<BaseProductResponse> page =
        baseProductService.findActive(Locale.forLanguageTag("eu"), PageRequest.of(0, 20));

    assertThat(page.content()).hasSize(1);
    assertThat(page.content().get(0).name()).isEqualTo("Whole milk");
  }

  /** Aplica fallback al primer idioma disponible cuando tampoco hay traducción en inglés. */
  @Test
  void findActive_fallsBackToFirstAvailable_whenEnglishAlsoUnavailable() {
    ProductFixture milk =
        new ProductFixture(
            "whole_milk", 1L, "L", null, "ML", new TranslationFixture("es", "Leche entera", null));
    when(baseProductRepository.findByIsActiveTrue(any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(milk)));
    when(categoryRepository.findAllById(any()))
        .thenReturn(List.of(category(1L, CATEGORY_PUBLIC_ID)));

    PagedResponse<BaseProductResponse> page =
        baseProductService.findActive(Locale.forLanguageTag("eu"), PageRequest.of(0, 20));

    assertThat(page.content()).hasSize(1);
    assertThat(page.content().get(0).name()).isEqualTo("Leche entera");
  }

  /** Devuelve la descripción en el idioma solicitado cuando existe traducción. */
  @Test
  void findActive_returnsDescriptionInRequestedLocale() {
    ProductFixture milk =
        new ProductFixture(
            "whole_milk",
            1L,
            "L",
            null,
            "ML",
            new TranslationFixture("es", "Leche entera", "Leche de vaca entera"),
            new TranslationFixture("en", "Whole milk", "Full-fat cow milk"),
            new TranslationFixture("eu", "Esne osoa", "Behi-esne osoa"));
    when(baseProductRepository.findByIsActiveTrue(any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(milk)));
    when(categoryRepository.findAllById(any()))
        .thenReturn(List.of(category(1L, CATEGORY_PUBLIC_ID)));

    PagedResponse<BaseProductResponse> page =
        baseProductService.findActive(Locale.forLanguageTag("en"), PageRequest.of(0, 20));

    assertThat(page.content().get(0).description()).isEqualTo("Full-fat cow milk");
  }

  /** Devuelve {@code null} en la descripción cuando la traducción no tiene descripción. */
  @Test
  void findActive_returnsNullDescription_whenTranslationHasNoDescription() {
    ProductFixture milk =
        new ProductFixture(
            "whole_milk", 1L, "L", null, "ML", new TranslationFixture("en", "Whole milk", null));
    when(baseProductRepository.findByIsActiveTrue(any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(milk)));
    when(categoryRepository.findAllById(any()))
        .thenReturn(List.of(category(1L, CATEGORY_PUBLIC_ID)));

    PagedResponse<BaseProductResponse> page =
        baseProductService.findActive(Locale.forLanguageTag("en"), PageRequest.of(0, 20));

    assertThat(page.content().get(0).description()).isNull();
  }

  /** Devuelve la página con los metadatos de paginación correctos. */
  @Test
  void findActive_returnsPaginationMetadata() {
    ProductFixture milk =
        new ProductFixture(
            "whole_milk", 1L, "L", null, "ML", new TranslationFixture("es", "Leche entera", null));
    Page<BaseProduct> springPage = new PageImpl<>(List.of(milk), PageRequest.of(2, 10), 42);
    when(baseProductRepository.findByIsActiveTrue(any(Pageable.class))).thenReturn(springPage);
    when(categoryRepository.findAllById(any()))
        .thenReturn(List.of(category(1L, CATEGORY_PUBLIC_ID)));

    PagedResponse<BaseProductResponse> page =
        baseProductService.findActive(Locale.forLanguageTag("es"), PageRequest.of(2, 10));

    assertThat(page.page()).isEqualTo(2);
    assertThat(page.size()).isEqualTo(10);
    assertThat(page.totalElements()).isEqualTo(42);
  }

  /** Aplica fallback a inglés en la descripción cuando el locale solicitado no tiene traducción. */
  @Test
  void findActive_fallsBackDescriptionToEnglish() {
    ProductFixture milk =
        new ProductFixture(
            "whole_milk",
            1L,
            "L",
            null,
            "ML",
            new TranslationFixture("es", "Leche entera", "Leche de vaca entera"),
            new TranslationFixture("en", "Whole milk", "Full-fat cow milk"));
    when(baseProductRepository.findByIsActiveTrue(any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(milk)));
    when(categoryRepository.findAllById(any()))
        .thenReturn(List.of(category(1L, CATEGORY_PUBLIC_ID)));

    PagedResponse<BaseProductResponse> page =
        baseProductService.findActive(Locale.forLanguageTag("eu"), PageRequest.of(0, 20));

    assertThat(page.content().get(0).name()).isEqualTo("Whole milk");
    assertThat(page.content().get(0).description()).isEqualTo("Full-fat cow milk");
  }

  /** Filtra por categoría resolviendo el identificador interno y mapeando el público resultante. */
  @Test
  void findActive_withCategory_filtersByInternalCategoryAndResolvesPublicId() {
    UUID categoryPublicId = UUID.randomUUID();
    Long internalCategoryId = 7L;
    when(categoryRepository.findByPublicId(categoryPublicId))
        .thenReturn(Optional.of(category(internalCategoryId, categoryPublicId)));
    ProductFixture milk =
        new ProductFixture(
            "whole_milk",
            internalCategoryId,
            "L",
            null,
            "ML",
            new TranslationFixture("es", "Leche entera", null));
    when(baseProductRepository.findByIsActiveTrueAndCategoryId(
            eq(internalCategoryId), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(milk)));
    when(categoryRepository.findAllById(any()))
        .thenReturn(List.of(category(internalCategoryId, categoryPublicId)));

    PagedResponse<BaseProductResponse> page =
        baseProductService.findActive(
            Locale.forLanguageTag("es"), PageRequest.of(0, 20), categoryPublicId, null);

    verify(baseProductRepository)
        .findByIsActiveTrueAndCategoryId(eq(internalCategoryId), any(Pageable.class));
    assertThat(page.content()).hasSize(1);
    assertThat(page.content().get(0).categoryId()).isEqualTo(categoryPublicId);
  }

  /** Devuelve una página vacía cuando la categoría del filtro no existe. */
  @Test
  void findActive_withUnknownCategory_returnsEmptyPage() {
    UUID unknownCategoryPublicId = UUID.randomUUID();
    when(categoryRepository.findByPublicId(unknownCategoryPublicId)).thenReturn(Optional.empty());

    PagedResponse<BaseProductResponse> page =
        baseProductService.findActive(
            Locale.forLanguageTag("es"), PageRequest.of(0, 20), unknownCategoryPublicId, null);

    assertThat(page.content()).isEmpty();
    assertThat(page.totalElements()).isZero();
    verify(baseProductRepository, never()).findByIsActiveTrueAndCategoryId(any(), any());
  }

  /** Usa la búsqueda textual cuando el filtro de texto está presente. */
  @Test
  void findActive_withText_usesTextSearch() {
    ProductFixture milk =
        new ProductFixture(
            "whole_milk", 1L, "L", null, "ML", new TranslationFixture("en", "Whole milk", null));
    when(baseProductRepository.findByIsActiveTrueAndText(eq("milk"), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(milk)));
    when(categoryRepository.findAllById(any()))
        .thenReturn(List.of(category(1L, CATEGORY_PUBLIC_ID)));

    PagedResponse<BaseProductResponse> page =
        baseProductService.findActive(Locale.ENGLISH, PageRequest.of(0, 20), null, "milk");

    assertThat(page.content()).hasSize(1);
    assertThat(page.content().get(0).name()).isEqualTo("Whole milk");
  }

  /** Devuelve el DTO del producto activo con la categoría resuelta a su identificador público. */
  @Test
  void findById_existingAndActive_returnsResponseWithResolvedCategory() {
    UUID productPublicId = UUID.randomUUID();
    ProductFixture milk =
        new ProductFixture(
            "whole_milk", 1L, "L", null, "ML", new TranslationFixture("en", "Whole milk", null));
    milk.setPublicId(productPublicId);
    when(baseProductRepository.findByPublicId(productPublicId)).thenReturn(Optional.of(milk));
    when(categoryRepository.findById(1L)).thenReturn(Optional.of(category(1L, CATEGORY_PUBLIC_ID)));

    BaseProductResponse response = baseProductService.findById(productPublicId, Locale.ENGLISH);

    assertThat(response.id()).isEqualTo(productPublicId);
    assertThat(response.categoryId()).isEqualTo(CATEGORY_PUBLIC_ID);
    assertThat(response.name()).isEqualTo("Whole milk");
  }

  /** Lanza {@code 404} cuando el identificador público no corresponde a ningún producto. */
  @Test
  void findById_missing_throwsNotFound() {
    when(baseProductRepository.findByPublicId(any())).thenReturn(Optional.empty());

    assertThatThrownBy(() -> baseProductService.findById(UUID.randomUUID(), Locale.ENGLISH))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.BASE_PRODUCT_NOT_FOUND));
  }

  /** Lanza {@code 404} cuando el producto existe pero está inactivo. */
  @Test
  void findById_inactive_throwsNotFound() {
    UUID productPublicId = UUID.randomUUID();
    ProductFixture milk =
        new ProductFixture(
            "whole_milk", 1L, "L", null, "ML", new TranslationFixture("en", "Whole milk", null));
    milk.setPublicId(productPublicId);
    milk.setIsActive(false);
    when(baseProductRepository.findByPublicId(productPublicId)).thenReturn(Optional.of(milk));

    assertThatThrownBy(() -> baseProductService.findById(productPublicId, Locale.ENGLISH))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.BASE_PRODUCT_NOT_FOUND));
  }

  /**
   * Crea el producto persistiendo el identificador interno de la categoría y devolviendo el
   * público.
   */
  @Test
  void create_withValidCategory_persistsInternalCategoryIdAndReturnsPublicId() {
    UUID categoryPublicId = UUID.randomUUID();
    when(categoryRepository.findByPublicId(categoryPublicId))
        .thenReturn(Optional.of(category(3L, categoryPublicId)));
    when(baseProductRepository.existsByCode("whole_milk")).thenReturn(false);
    CreateBaseProductRequest request =
        new CreateBaseProductRequest(
            "whole_milk",
            categoryPublicId,
            UnitEnum.L,
            null,
            CaloriesPerEnum.ML,
            true,
            List.of(new CreateBaseProductRequest.ProductTranslation("en", "Whole milk", null)));
    ProductFixture saved =
        new ProductFixture(
            "whole_milk", 3L, "L", null, "ML", new TranslationFixture("en", "Whole milk", null));
    when(baseProductRepository.save(any())).thenReturn(saved);

    BaseProductResponse response = baseProductService.create(request, Locale.ENGLISH);

    assertThat(response.code()).isEqualTo("whole_milk");
    assertThat(response.categoryId()).isEqualTo(categoryPublicId);
  }

  /** Lanza {@code 400} cuando la categoría indicada no existe. */
  @Test
  void create_withUnknownCategory_throwsInvalidCategory() {
    UUID categoryPublicId = UUID.randomUUID();
    when(categoryRepository.findByPublicId(categoryPublicId)).thenReturn(Optional.empty());
    CreateBaseProductRequest request =
        new CreateBaseProductRequest(
            "whole_milk",
            categoryPublicId,
            UnitEnum.L,
            null,
            CaloriesPerEnum.ML,
            true,
            List.of(new CreateBaseProductRequest.ProductTranslation("en", "Whole milk", null)));

    assertThatThrownBy(() -> baseProductService.create(request, Locale.ENGLISH))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_CATEGORY));
  }

  /**
   * Aplica el cambio de categoría y devuelve el identificador público resuelto del estado final.
   */
  @Test
  void update_withCategoryChange_updatesInternalCategoryAndResolvesNewPublicId() {
    UUID productPublicId = UUID.randomUUID();
    UUID newCategoryPublicId = UUID.randomUUID();
    ProductFixture milk =
        new ProductFixture(
            "whole_milk", 1L, "L", null, "ML", new TranslationFixture("en", "Whole milk", null));
    milk.setPublicId(productPublicId);
    when(baseProductRepository.findByPublicId(productPublicId)).thenReturn(Optional.of(milk));
    when(categoryRepository.findByPublicId(newCategoryPublicId))
        .thenReturn(Optional.of(category(9L, newCategoryPublicId)));
    UpdateBaseProductRequest request =
        new UpdateBaseProductRequest(null, newCategoryPublicId, null, null, null, null, null);
    when(baseProductRepository.save(any())).thenReturn(milk);
    when(categoryRepository.findById(9L))
        .thenReturn(Optional.of(category(9L, newCategoryPublicId)));

    BaseProductResponse response =
        baseProductService.update(productPublicId, request, Locale.ENGLISH);

    assertThat(response.categoryId()).isEqualTo(newCategoryPublicId);
  }

  /** Lanza {@code 404} al editar un producto inexistente. */
  @Test
  void update_missing_throwsNotFound() {
    when(baseProductRepository.findByPublicId(any())).thenReturn(Optional.empty());
    UpdateBaseProductRequest request =
        new UpdateBaseProductRequest(null, null, null, null, null, null, null);

    assertThatThrownBy(() -> baseProductService.update(UUID.randomUUID(), request, Locale.ENGLISH))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.BASE_PRODUCT_NOT_FOUND));
  }

  /** Elimina el producto existente localizado por su identificador público. */
  @Test
  void delete_existing_deletesProduct() {
    UUID productPublicId = UUID.randomUUID();
    ProductFixture milk =
        new ProductFixture(
            "whole_milk", 1L, "L", null, "ML", new TranslationFixture("en", "Whole milk", null));
    when(baseProductRepository.findByPublicId(productPublicId)).thenReturn(Optional.of(milk));

    baseProductService.delete(productPublicId);

    verify(baseProductRepository).delete(milk);
  }

  /** Lanza {@code 404} al borrar un producto inexistente. */
  @Test
  void delete_missing_throwsNotFound() {
    when(baseProductRepository.findByPublicId(any())).thenReturn(Optional.empty());

    assertThatThrownBy(() -> baseProductService.delete(UUID.randomUUID()))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.BASE_PRODUCT_NOT_FOUND));
  }

  /** Construye una categoría con los identificadores interno y público indicados. */
  private Category category(Long id, UUID publicId) {
    Category category = new Category();
    category.setId(id);
    category.setPublicId(publicId);
    return category;
  }

  /**
   * POJO auxiliar para construir traducciones de test. El {@link ProductFixture} las convierte en
   * {@link BaseProductTranslation} al construirse.
   */
  private static class TranslationFixture {

    private final String locale;

    private final String name;

    private final String description;

    TranslationFixture(String locale, String name, String description) {
      this.locale = locale;
      this.name = name;
      this.description = description;
    }
  }

  /**
   * Subclase de {@link BaseProduct} que recibe traducciones como {@link TranslationFixture} y las
   * convierte en {@link BaseProductTranslation} poblando la colección {@code translations}
   * heredada.
   */
  private static class ProductFixture extends BaseProduct {

    ProductFixture(
        String code,
        Long categoryId,
        String defaultUnit,
        Integer calories,
        String caloriesPer,
        TranslationFixture... fixtures) {
      setCode(code);
      setCategoryId(categoryId);
      setDefaultUnit(UnitEnum.valueOf(defaultUnit));
      setCalories(calories);
      setCaloriesPer(CaloriesPerEnum.valueOf(caloriesPer));
      setIsActive(true);
      for (TranslationFixture f : fixtures) {
        BaseProductTranslation t = new BaseProductTranslation();
        t.setLocale(f.locale);
        t.setName(f.name);
        t.setDescription(f.description);
        t.setBaseProduct(this);
        getTranslations().add(t);
      }
    }
  }
}
