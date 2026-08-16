package dev.ivborrezo.shoppinglist.product.service.product.service;

import dev.ivborrezo.shoppinglist.product.service.category.repository.CategoryRepository;
import dev.ivborrezo.shoppinglist.product.service.common.dto.PagedResponse;
import dev.ivborrezo.shoppinglist.product.service.product.dto.BaseProductResponse;
import dev.ivborrezo.shoppinglist.product.service.product.dto.CreateBaseProductRequest;
import dev.ivborrezo.shoppinglist.product.service.product.dto.UpdateBaseProductRequest;
import dev.ivborrezo.shoppinglist.product.service.product.entity.BaseProduct;
import dev.ivborrezo.shoppinglist.product.service.product.entity.BaseProductTranslation;
import dev.ivborrezo.shoppinglist.product.service.product.repository.BaseProductRepository;
import dev.ivborrezo.shoppinglist.product.service.product.repository.BaseProductTranslationRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Servicio de gestión de productos base del catálogo con resolución de nombres y descripciones
 * localizados.
 */
@Service
@Transactional(readOnly = true)
public class BaseProductService {

  private static final Set<String> SUPPORTED_LOCALES = Set.of("es", "en", "eu");

  private static final String FALLBACK_LOCALE = "en";

  private final BaseProductRepository baseProductRepository;

  private final CategoryRepository categoryRepository;

  private final BaseProductTranslationRepository translationRepository;

  /**
   * Construye el servicio de productos base con los repositorios de productos, categorías y
   * traducciones.
   *
   * @param baseProductRepository repositorio de productos base
   * @param categoryRepository repositorio de categorías
   * @param translationRepository repositorio de traducciones de productos base
   */
  public BaseProductService(
      BaseProductRepository baseProductRepository,
      CategoryRepository categoryRepository,
      BaseProductTranslationRepository translationRepository) {
    this.baseProductRepository = baseProductRepository;
    this.categoryRepository = categoryRepository;
    this.translationRepository = translationRepository;
  }

  /**
   * Devuelve los productos base activos paginados, con nombres y descripciones localizados.
   *
   * @param locale idioma en el que se quieren los textos localizados
   * @param pageable parámetros de paginación
   * @return página de DTOs con los productos activos y sus textos localizados
   */
  public PagedResponse<BaseProductResponse> findActive(Locale locale, Pageable pageable) {
    return findActive(locale, pageable, null, null);
  }

  /**
   * Devuelve los productos base activos paginados con filtros opcionales por categoría o búsqueda
   * textual.
   *
   * @param locale idioma en el que se quieren los textos localizados
   * @param pageable parámetros de paginación
   * @param categoryId identificador de categoría para filtrar; {@code null} para no filtrar
   * @param text término de búsqueda textual sobre el nombre localizado; {@code null} para no
   *     filtrar
   * @return página de DTOs con los productos activos y sus textos localizados
   */
  public PagedResponse<BaseProductResponse> findActive(
      Locale locale, Pageable pageable, Long categoryId, String text) {
    Page<BaseProduct> page;
    if (text != null && !text.isBlank()) {
      page = baseProductRepository.findByIsActiveTrueAndText(text, pageable);
    } else if (categoryId != null) {
      page = baseProductRepository.findByIsActiveTrueAndCategoryId(categoryId, pageable);
    } else {
      page = baseProductRepository.findByIsActiveTrue(pageable);
    }

    List<BaseProductResponse> responses =
        page.getContent().stream()
            .map(
                bp ->
                    BaseProductResponse.from(
                        bp, resolveName(bp, locale), resolveDescription(bp, locale)))
            .toList();

    return new PagedResponse<>(
        responses, page.getNumber(), page.getSize(), page.getTotalElements());
  }

  /**
   * Busca un producto base por su identificador con nombre y descripción localizados.
   *
   * @param id identificador del producto base a recuperar
   * @param locale idioma en el que se quieren los textos localizados
   * @return DTO del producto encontrado con sus textos localizados
   * @throws ResponseStatusException con {@code 404} si el producto no existe o está inactivo
   */
  public BaseProductResponse findById(Long id, Locale locale) {
    BaseProduct product =
        baseProductRepository
            .findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    if (!product.getIsActive()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
    return BaseProductResponse.from(
        product, resolveName(product, locale), resolveDescription(product, locale));
  }

  /**
   * Crea un producto base con sus traducciones y devuelve el DTO con el nombre y la descripción
   * localizados.
   *
   * @param request petición con los datos del producto base y sus traducciones
   * @param locale idioma en el que se devuelven los textos localizados del producto creado
   * @return DTO del producto base recién creado con sus textos localizados
   * @throws ResponseStatusException con {@code 400} si la categoría no existe
   * @throws ResponseStatusException con {@code 409} si ya existe un producto con ese código
   * @throws IllegalArgumentException si alguna traducción usa un locale no soportado
   */
  @Transactional
  public BaseProductResponse create(CreateBaseProductRequest request, Locale locale) {
    if (!categoryRepository.existsById(request.categoryId())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Category not found");
    }

    if (baseProductRepository.existsByCode(request.code())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Product code already exists");
    }

    validateSupportedLocales(request.translations());

    BaseProduct product = new BaseProduct();
    product.setCode(request.code());
    product.setCategoryId(request.categoryId());
    product.setDefaultUnit(request.defaultUnit());
    product.setCalories(request.calories());
    product.setCaloriesPer(request.caloriesPer());
    product.setIsActive(request.isActive());
    product.setTranslations(new HashSet<>());

    for (CreateBaseProductRequest.ProductTranslation t : request.translations()) {
      BaseProductTranslation translation = new BaseProductTranslation();
      translation.setLocale(t.locale());
      translation.setName(t.name());
      translation.setDescription(t.description());
      translation.setBaseProduct(product);
      product.getTranslations().add(translation);
    }

    BaseProduct saved = baseProductRepository.save(product);
    return BaseProductResponse.from(
        saved, resolveName(saved, locale), resolveDescription(saved, locale));
  }

  /**
   * Edita parcialmente un producto base aplicando solo los campos no nulos del request.
   *
   * <p>Si {@code code} cambia y ya existe otro producto con ese código, se rechaza con 409. Si
   * {@code translations} está presente, reemplaza el conjunto completo — no fusiona. La validación
   * de locales soportados se aplica tanto en creación como en edición.
   *
   * @param id identificador del producto base a editar
   * @param request petición con los campos a modificar; solo los no nulos se aplican
   * @param locale idioma en el que se devuelven los textos localizados del producto editado
   * @return DTO del producto base tras aplicar los cambios, con sus textos localizados
   * @throws ResponseStatusException con {@code 404} si el producto no existe
   * @throws ResponseStatusException con {@code 400} si la categoría indicada no existe
   * @throws ResponseStatusException con {@code 409} si el nuevo código ya está en uso por otro
   *     producto
   * @throws IllegalArgumentException si alguna traducción usa un locale no soportado
   */
  @Transactional
  public BaseProductResponse update(Long id, UpdateBaseProductRequest request, Locale locale) {
    BaseProduct product =
        baseProductRepository
            .findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

    if (request.code() != null && !request.code().equals(product.getCode())) {
      if (baseProductRepository.existsByCode(request.code())) {
        throw new ResponseStatusException(HttpStatus.CONFLICT, "Product code already exists");
      }
      product.setCode(request.code());
    }

    if (request.categoryId() != null) {
      if (!categoryRepository.existsById(request.categoryId())) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Category not found");
      }
      product.setCategoryId(request.categoryId());
    }

    if (request.defaultUnit() != null) {
      product.setDefaultUnit(request.defaultUnit());
    }

    if (request.calories() != null) {
      product.setCalories(request.calories());
    }

    if (request.caloriesPer() != null) {
      product.setCaloriesPer(request.caloriesPer());
    }

    if (request.isActive() != null) {
      product.setIsActive(request.isActive());
    }

    if (request.translations() != null) {
      for (UpdateBaseProductRequest.ProductTranslation t : request.translations()) {
        if (!SUPPORTED_LOCALES.contains(t.locale())) {
          throw new IllegalArgumentException("Unsupported locale");
        }
      }

      translationRepository.deleteAllByProductId(product.getId());
      product.getTranslations().clear();

      for (UpdateBaseProductRequest.ProductTranslation t : request.translations()) {
        BaseProductTranslation translation = new BaseProductTranslation();
        translation.setLocale(t.locale());
        translation.setName(t.name());
        translation.setDescription(t.description());
        translation.setBaseProduct(product);
        product.getTranslations().add(translation);
      }
    }

    BaseProduct saved = baseProductRepository.save(product);
    return BaseProductResponse.from(
        saved, resolveName(saved, locale), resolveDescription(saved, locale));
  }

  /**
   * Elimina físicamente un producto base y sus traducciones en cascada.
   *
   * @param id identificador del producto base a eliminar
   * @throws ResponseStatusException con {@code 404} si el producto no existe
   */
  @Transactional
  public void delete(Long id) {
    BaseProduct product =
        baseProductRepository
            .findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    baseProductRepository.delete(product);
  }

  private void validateSupportedLocales(
      List<CreateBaseProductRequest.ProductTranslation> translations) {
    boolean allSupported =
        translations.stream().allMatch(t -> SUPPORTED_LOCALES.contains(t.locale()));
    if (!allSupported) {
      throw new IllegalArgumentException("Unsupported locale");
    }
  }

  /**
   * Resuelve el nombre localizado de un producto base aplicando: coincidencia exacta con el locale
   * solicitado → fallback a {@value #FALLBACK_LOCALE} → primer idioma disponible.
   */
  String resolveName(BaseProduct product, Locale locale) {
    Set<BaseProductTranslation> translations = product.getTranslations();
    if (translations.isEmpty()) {
      return null;
    }
    String localeTag = locale.toLanguageTag();

    String exact =
        translations.stream()
            .filter(t -> t.getLocale().equals(localeTag))
            .map(BaseProductTranslation::getName)
            .findFirst()
            .orElse(null);
    if (exact != null) {
      return exact;
    }

    String english =
        translations.stream()
            .filter(t -> t.getLocale().equals(FALLBACK_LOCALE))
            .map(BaseProductTranslation::getName)
            .findFirst()
            .orElse(null);
    if (english != null) {
      return english;
    }

    return translations.iterator().next().getName();
  }

  /**
   * Resuelve la descripción localizada de un producto base con la misma estrategia de fallback que
   * {@link #resolveName(BaseProduct, Locale)}. Si la traducción resuelta no tiene descripción,
   * devuelve {@code null}.
   */
  String resolveDescription(BaseProduct product, Locale locale) {
    Set<BaseProductTranslation> translations = product.getTranslations();
    if (translations.isEmpty()) {
      return null;
    }
    String localeTag = locale.toLanguageTag();

    BaseProductTranslation exact =
        translations.stream().filter(t -> t.getLocale().equals(localeTag)).findFirst().orElse(null);
    if (exact != null) {
      return exact.getDescription();
    }

    BaseProductTranslation english =
        translations.stream()
            .filter(t -> t.getLocale().equals(FALLBACK_LOCALE))
            .findFirst()
            .orElse(null);
    if (english != null) {
      return english.getDescription();
    }

    return translations.iterator().next().getDescription();
  }
}
