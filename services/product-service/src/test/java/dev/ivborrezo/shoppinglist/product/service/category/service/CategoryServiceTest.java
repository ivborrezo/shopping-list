package dev.ivborrezo.shoppinglist.product.service.category.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import dev.ivborrezo.shoppinglist.product.service.category.dto.CategoryResponse;
import dev.ivborrezo.shoppinglist.product.service.category.entity.Category;
import dev.ivborrezo.shoppinglist.product.service.category.entity.CategoryTranslation;
import dev.ivborrezo.shoppinglist.product.service.category.repository.CategoryRepository;
import dev.ivborrezo.shoppinglist.product.service.common.dto.PagedResponse;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

  @Mock private CategoryRepository categoryRepository;

  private CategoryService categoryService;

  @BeforeEach
  void setUp() {
    categoryService = new CategoryService(categoryRepository);
  }

  @Test
  void findActive_returnsNameInRequestedLocale() {
    CategoryFixture dairy =
        new CategoryFixture(
            new TranslationFixture("es", "Lácteos"),
            new TranslationFixture("en", "Dairy"),
            new TranslationFixture("eu", "Esnekiak"));
    when(categoryRepository.findByIsActiveTrue(any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(dairy)));

    PagedResponse<CategoryResponse> page =
        categoryService.findActive(Locale.forLanguageTag("eu"), PageRequest.of(0, 20));

    assertThat(page.content()).hasSize(1);
    assertThat(page.content().get(0).name()).isEqualTo("Esnekiak");
    assertThat(page.totalElements()).isEqualTo(1);
  }

  @Test
  void findActive_fallsBackToEnglish_whenRequestedLocaleUnavailable() {
    CategoryFixture dairy =
        new CategoryFixture(
            new TranslationFixture("es", "Lácteos"), new TranslationFixture("en", "Dairy"));
    when(categoryRepository.findByIsActiveTrue(any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(dairy)));

    PagedResponse<CategoryResponse> page =
        categoryService.findActive(Locale.forLanguageTag("eu"), PageRequest.of(0, 20));

    assertThat(page.content()).hasSize(1);
    assertThat(page.content().get(0).name()).isEqualTo("Dairy");
  }

  @Test
  void findActive_fallsBackToFirstAvailable_whenEnglishAlsoUnavailable() {
    CategoryFixture dairy = new CategoryFixture(new TranslationFixture("es", "Lácteos"));
    when(categoryRepository.findByIsActiveTrue(any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(dairy)));

    PagedResponse<CategoryResponse> page =
        categoryService.findActive(Locale.forLanguageTag("eu"), PageRequest.of(0, 20));

    assertThat(page.content()).hasSize(1);
    assertThat(page.content().get(0).name()).isEqualTo("Lácteos");
  }

  @Test
  void findActive_returnsAllActiveCategories() {
    CategoryFixture dairy = new CategoryFixture(new TranslationFixture("es", "Lácteos"));
    CategoryFixture bakery = new CategoryFixture(new TranslationFixture("es", "Panadería"));
    CategoryFixture produce =
        new CategoryFixture(new TranslationFixture("es", "Frutas y verduras"));
    when(categoryRepository.findByIsActiveTrue(any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(dairy, bakery, produce)));

    PagedResponse<CategoryResponse> page =
        categoryService.findActive(Locale.forLanguageTag("es"), PageRequest.of(0, 20));

    assertThat(page.content()).hasSize(3);
  }

  @Test
  void findActive_returnsEmptyList_whenNoActiveCategories() {
    when(categoryRepository.findByIsActiveTrue(any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of()));

    PagedResponse<CategoryResponse> page =
        categoryService.findActive(Locale.forLanguageTag("es"), PageRequest.of(0, 20));

    assertThat(page.content()).isEmpty();
    assertThat(page.totalElements()).isEqualTo(0);
  }

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
