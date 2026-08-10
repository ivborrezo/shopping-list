package dev.ivborrezo.shoppinglist.product.service.category.service;

import dev.ivborrezo.shoppinglist.product.service.category.dto.CategoryResponseDto;
import dev.ivborrezo.shoppinglist.product.service.category.dto.CreateCategoryRequest;
import dev.ivborrezo.shoppinglist.product.service.category.entity.Category;
import dev.ivborrezo.shoppinglist.product.service.category.entity.CategoryTranslation;
import dev.ivborrezo.shoppinglist.product.service.category.repository.CategoryRepository;
import dev.ivborrezo.shoppinglist.product.service.common.dto.PagedResponse;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Servicio de gestión de categorías del catálogo con resolución de nombres localizados. */
@Service
@Transactional(readOnly = true)
public class CategoryService {

  private static final String FALLBACK_LOCALE = "en";

  private static final Set<String> SUPPORTED_LOCALES = Set.of("es", "en", "eu");

  private final CategoryRepository categoryRepository;

  public CategoryService(CategoryRepository categoryRepository) {
    this.categoryRepository = categoryRepository;
  }

  /**
   * Devuelve las categorías activas del catálogo paginadas, con el nombre resuelto al idioma
   * solicitado.
   *
   * @param locale idioma en el que se quieren los nombres de las categorías
   * @param pageable parámetros de paginación (número de página, tamaño)
   * @return página de DTOs con las categorías activas y sus nombres localizados; vacía si no hay
   */
  public PagedResponse<CategoryResponseDto> findActive(Locale locale, Pageable pageable) {
    Page<Category> page = categoryRepository.findByIsActiveTrue(pageable);
    List<CategoryResponseDto> dtos =
        page.getContent().stream()
            .map(c -> CategoryResponseDto.from(c, resolveName(c, locale)))
            .toList();
    return new PagedResponse<>(dtos, page.getNumber(), page.getSize(), page.getTotalElements());
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
   * Crea una categoría con sus traducciones y devuelve el DTO con el nombre localizado.
   *
   * @param request petición con el código, estado activo y lista de traducciones
   * @param locale idioma en el que se devuelve el nombre de la categoría creada
   * @return DTO de la categoría recién creada con el nombre localizado al idioma solicitado
   * @throws IllegalArgumentException si alguna traducción usa un locale no soportado
   */
  @Transactional
  public CategoryResponseDto create(CreateCategoryRequest request, Locale locale) {
    validateSupportedLocales(request.translations());

    Category category = new Category();
    category.setCode(request.code());
    category.setIsActive(request.isActive());

    for (CreateCategoryRequest.Translation t : request.translations()) {
      CategoryTranslation translation = new CategoryTranslation();
      translation.setLocale(t.locale());
      translation.setName(t.name());
      translation.setCategory(category);
      category.getTranslations().add(translation);
    }

    Category saved = categoryRepository.save(category);
    return CategoryResponseDto.from(saved, resolveName(saved, locale));
  }

  private void validateSupportedLocales(List<CreateCategoryRequest.Translation> translations) {
    boolean allSupported =
        translations.stream().allMatch(t -> SUPPORTED_LOCALES.contains(t.locale()));
    if (!allSupported) {
      throw new IllegalArgumentException("Unsupported locale");
    }
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
