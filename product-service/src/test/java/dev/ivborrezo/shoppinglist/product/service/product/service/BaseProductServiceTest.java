package dev.ivborrezo.shoppinglist.product.service.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import dev.ivborrezo.shoppinglist.product.service.category.repository.CategoryRepository;
import dev.ivborrezo.shoppinglist.product.service.common.dto.PagedResponse;
import dev.ivborrezo.shoppinglist.product.service.product.dto.BaseProductResponseDto;
import dev.ivborrezo.shoppinglist.product.service.product.entity.BaseProduct;
import dev.ivborrezo.shoppinglist.product.service.product.entity.BaseProductTranslation;
import dev.ivborrezo.shoppinglist.product.service.product.repository.BaseProductRepository;
import java.util.Locale;
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
 * Test unitario de la resolución de nombres y descripciones localizados de {@link
 * BaseProductService}.
 *
 * <p>Ejercita los métodos {@code findActive} y {@code findById} con el repositorio mockeado,
 * cubriendo la traducción en el idioma solicitado, el fallback a inglés y el fallback al primer
 * idioma disponible, tanto para {@code name} como para {@code description}.
 */
@ExtendWith(MockitoExtension.class)
class BaseProductServiceTest {

  @Mock private BaseProductRepository baseProductRepository;

  @Mock private CategoryRepository categoryRepository;

  private BaseProductService baseProductService;

  /** Instancia el servicio bajo test con los repositorios mockeados. */
  @BeforeEach
  void setUp() {
    baseProductService = new BaseProductService(baseProductRepository, categoryRepository);
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
        .thenReturn(new PageImpl<>(java.util.List.of(milk)));

    PagedResponse<BaseProductResponseDto> page =
        baseProductService.findActive(Locale.forLanguageTag("eu"), PageRequest.of(0, 20));

    assertThat(page.content()).hasSize(1);
    assertThat(page.content().get(0).name()).isEqualTo("Esne osoa");
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
        .thenReturn(new PageImpl<>(java.util.List.of(milk)));

    PagedResponse<BaseProductResponseDto> page =
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
        .thenReturn(new PageImpl<>(java.util.List.of(milk)));

    PagedResponse<BaseProductResponseDto> page =
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
        .thenReturn(new PageImpl<>(java.util.List.of(milk)));

    PagedResponse<BaseProductResponseDto> page =
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
        .thenReturn(new PageImpl<>(java.util.List.of(milk)));

    PagedResponse<BaseProductResponseDto> page =
        baseProductService.findActive(Locale.forLanguageTag("en"), PageRequest.of(0, 20));

    assertThat(page.content().get(0).description()).isNull();
  }

  /** Devuelve la página con los metadatos de paginación correctos. */
  @Test
  void findActive_returnsPaginationMetadata() {
    ProductFixture milk =
        new ProductFixture(
            "whole_milk", 1L, "L", null, "ML", new TranslationFixture("es", "Leche entera", null));
    Page<BaseProduct> springPage =
        new PageImpl<>(java.util.List.of(milk), PageRequest.of(2, 10), 42);
    when(baseProductRepository.findByIsActiveTrue(any(Pageable.class))).thenReturn(springPage);

    PagedResponse<BaseProductResponseDto> page =
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
        .thenReturn(new PageImpl<>(java.util.List.of(milk)));

    PagedResponse<BaseProductResponseDto> page =
        baseProductService.findActive(Locale.forLanguageTag("eu"), PageRequest.of(0, 20));

    assertThat(page.content().get(0).name()).isEqualTo("Whole milk");
    assertThat(page.content().get(0).description()).isEqualTo("Full-fat cow milk");
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
      setDefaultUnit(defaultUnit);
      setCalories(calories);
      setCaloriesPer(caloriesPer);
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
