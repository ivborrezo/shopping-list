package dev.ivborrezo.shoppinglist.product.service.category.service;

import dev.ivborrezo.shoppinglist.product.service.category.dto.CategoryResponseDto;
import dev.ivborrezo.shoppinglist.product.service.category.entity.Category;
import dev.ivborrezo.shoppinglist.product.service.category.entity.CategoryTranslation;
import dev.ivborrezo.shoppinglist.product.service.category.repository.CategoryRepository;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Servicio de gestión de categorías del catálogo con resolución de nombres localizados. */
@Service
@Transactional(readOnly = true)
public class CategoryService {

  private static final String FALLBACK_LOCALE = "en";

  private final CategoryRepository categoryRepository;

  public CategoryService(CategoryRepository categoryRepository) {
    this.categoryRepository = categoryRepository;
  }

  /**
   * Devuelve las categorías activas del catálogo con el nombre resuelto al idioma solicitado.
   *
   * @param locale idioma en el que se quieren los nombres de las categorías
   * @return lista de DTOs con las categorías activas y sus nombres localizados; vacía si no hay
   */
  public List<CategoryResponseDto> findActive(Locale locale) {
    return categoryRepository.findByIsActiveTrue().stream()
        .map(c -> CategoryResponseDto.from(c, resolveName(c, locale)))
        .toList();
  }

  /**
   * Busca una categoría por su identificador con el nombre resuelto al idioma solicitado.
   *
   * @param id identificador de la categoría a recuperar
   * @param locale idioma en el que se quiere el nombre de la categoría
   * @return DTO de la categoría encontrada con su nombre localizado
   * @throws ResponseStatusException con {@code 404} si la categoría no existe
   */
  public CategoryResponseDto findById(Long id, Locale locale) {
    return categoryRepository
        .findById(id)
        .map(c -> CategoryResponseDto.from(c, resolveName(c, locale)))
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
  }

  /**
   * Resuelve el nombre localizado de una categoría aplicando: coincidencia exacta con el locale
   * solicitado → fallback a {@value #FALLBACK_LOCALE} → primer idioma disponible.
   */
  private String resolveName(Category category, Locale locale) {
    Set<CategoryTranslation> translations = category.getTranslations();
    if (translations.isEmpty()) {
      return null;
    }
    String localeTag = locale.toLanguageTag();

    String exact =
        translations.stream()
            .filter(t -> t.getLocale().equals(localeTag))
            .map(CategoryTranslation::getName)
            .findFirst()
            .orElse(null);
    if (exact != null) {
      return exact;
    }

    String english =
        translations.stream()
            .filter(t -> t.getLocale().equals(FALLBACK_LOCALE))
            .map(CategoryTranslation::getName)
            .findFirst()
            .orElse(null);
    if (english != null) {
      return english;
    }

    return translations.iterator().next().getName();
  }
}
