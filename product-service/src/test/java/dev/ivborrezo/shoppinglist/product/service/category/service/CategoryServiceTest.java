package dev.ivborrezo.shoppinglist.product.service.category.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import dev.ivborrezo.shoppinglist.product.service.category.dto.CategoryResponseDto;
import dev.ivborrezo.shoppinglist.product.service.category.entity.Category;
import dev.ivborrezo.shoppinglist.product.service.category.entity.CategoryTranslation;
import dev.ivborrezo.shoppinglist.product.service.category.repository.CategoryRepository;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Test unitario de la resolución de nombre localizado de {@link CategoryService}.
 *
 * <p>Ejercita {@code findActive(Locale)} con el repositorio mockeado, cubriendo la traducción en el
 * idioma solicitado, el fallback a inglés y el fallback al primer idioma disponible.
 */
@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

  @Mock private CategoryRepository categoryRepository;

  private CategoryService categoryService;

  /** Instancia el servicio bajo test con el repositorio mockeado. */
  @BeforeEach
  void setUp() {
    categoryService = new CategoryService(categoryRepository);
  }

  /** Devuelve el nombre en el idioma solicitado cuando existe traducción. */
  @Test
  void findActive_returnsNameInRequestedLocale() {
    CategoryFixture dairy =
        new CategoryFixture(
            new TranslationFixture("es", "Lácteos"),
            new TranslationFixture("en", "Dairy"),
            new TranslationFixture("eu", "Esnekiak"));
    when(categoryRepository.findByIsActiveTrue()).thenReturn(List.of(dairy));

    List<CategoryResponseDto> categories = categoryService.findActive(Locale.forLanguageTag("eu"));

    assertThat(categories).hasSize(1);
    assertThat(categories.get(0).name()).isEqualTo("Esnekiak");
  }

  /** Aplica fallback a inglés cuando el idioma solicitado no tiene traducción. */
  @Test
  void findActive_fallsBackToEnglish_whenRequestedLocaleUnavailable() {
    CategoryFixture dairy =
        new CategoryFixture(
            new TranslationFixture("es", "Lácteos"), new TranslationFixture("en", "Dairy"));
    when(categoryRepository.findByIsActiveTrue()).thenReturn(List.of(dairy));

    List<CategoryResponseDto> categories = categoryService.findActive(Locale.forLanguageTag("eu"));

    assertThat(categories).hasSize(1);
    assertThat(categories.get(0).name()).isEqualTo("Dairy");
  }

  /** Aplica fallback al primer idioma disponible cuando tampoco hay traducción en inglés. */
  @Test
  void findActive_fallsBackToFirstAvailable_whenEnglishAlsoUnavailable() {
    CategoryFixture dairy = new CategoryFixture(new TranslationFixture("es", "Lácteos"));
    when(categoryRepository.findByIsActiveTrue()).thenReturn(List.of(dairy));

    List<CategoryResponseDto> categories = categoryService.findActive(Locale.forLanguageTag("eu"));

    assertThat(categories).hasSize(1);
    assertThat(categories.get(0).name()).isEqualTo("Lácteos");
  }

  /** Devuelve todas las categorías activas que devuelve el repositorio. */
  @Test
  void findActive_returnsAllActiveCategories() {
    CategoryFixture dairy = new CategoryFixture(new TranslationFixture("es", "Lácteos"));
    CategoryFixture bakery = new CategoryFixture(new TranslationFixture("es", "Panadería"));
    CategoryFixture produce =
        new CategoryFixture(new TranslationFixture("es", "Frutas y verduras"));
    when(categoryRepository.findByIsActiveTrue()).thenReturn(List.of(dairy, bakery, produce));

    List<CategoryResponseDto> categories = categoryService.findActive(Locale.forLanguageTag("es"));

    assertThat(categories).hasSize(3);
  }

  /** Devuelve una lista vacía cuando no hay categorías activas. */
  @Test
  void findActive_returnsEmptyList_whenNoActiveCategories() {
    when(categoryRepository.findByIsActiveTrue()).thenReturn(List.of());

    List<CategoryResponseDto> categories = categoryService.findActive(Locale.forLanguageTag("es"));

    assertThat(categories).isEmpty();
  }

  /**
   * POJO auxiliar para construir traducciones de test. El {@link CategoryFixture} las convierte en
   * {@link CategoryTranslation} al construirse.
   */
  private static class TranslationFixture {

    private final String locale;

    private final String name;

    TranslationFixture(String locale, String name) {
      this.locale = locale;
      this.name = name;
    }

    String getLocale() {
      return locale;
    }

    String getName() {
      return name;
    }
  }

  /**
   * Subclase de {@link Category} que recibe traducciones como {@link TranslationFixture} y las
   * convierte en {@link CategoryTranslation} poblando la colección {@code translations} heredada.
   */
  private static class CategoryFixture extends Category {

    CategoryFixture(TranslationFixture... fixtures) {
      setIsActive(true);
      for (TranslationFixture f : fixtures) {
        CategoryTranslation ct = new CategoryTranslation();
        ct.setLocale(f.getLocale());
        ct.setName(f.getName());
        ct.setCategory(this);
        getTranslations().add(ct);
      }
    }
  }
}
