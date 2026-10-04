package dev.ivborrezo.shoppinglist.product.service.product.service;

import dev.ivborrezo.shoppinglist.product.service.category.entity.Category;
import dev.ivborrezo.shoppinglist.product.service.category.repository.CategoryRepository;
import dev.ivborrezo.shoppinglist.product.service.common.BusinessException;
import dev.ivborrezo.shoppinglist.product.service.common.CaloriesPerEnum;
import dev.ivborrezo.shoppinglist.product.service.common.ErrorCode;
import dev.ivborrezo.shoppinglist.product.service.common.UnitEnum;
import dev.ivborrezo.shoppinglist.product.service.common.dto.PagedResponse;
import dev.ivborrezo.shoppinglist.product.service.product.dto.CreateUserProductRequest;
import dev.ivborrezo.shoppinglist.product.service.product.dto.UpdateUserProductRequest;
import dev.ivborrezo.shoppinglist.product.service.product.dto.UserProductResponse;
import dev.ivborrezo.shoppinglist.product.service.product.entity.BaseProduct;
import dev.ivborrezo.shoppinglist.product.service.product.entity.UserProduct;
import dev.ivborrezo.shoppinglist.product.service.product.repository.BaseProductRepository;
import dev.ivborrezo.shoppinglist.product.service.product.repository.UserProductRepository;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Servicio de gestión de los productos de usuario del catálogo personal. */
@Service
@Transactional(readOnly = true)
public class UserProductService {

  private final UserProductRepository userProductRepository;

  private final BaseProductRepository baseProductRepository;

  private final BaseProductService baseProductService;

  private final CategoryRepository categoryRepository;

  /**
   * Construye el servicio de productos de usuario con los repositorios de productos de usuario,
   * productos base y categorías, y el servicio de productos base para resolver los campos copiados.
   *
   * @param userProductRepository repositorio de productos de usuario
   * @param baseProductRepository repositorio de productos base
   * @param baseProductService servicio de productos base para resolver campos localizados
   * @param categoryRepository repositorio de categorías
   */
  public UserProductService(
      UserProductRepository userProductRepository,
      BaseProductRepository baseProductRepository,
      BaseProductService baseProductService,
      CategoryRepository categoryRepository) {
    this.userProductRepository = userProductRepository;
    this.baseProductRepository = baseProductRepository;
    this.baseProductService = baseProductService;
    this.categoryRepository = categoryRepository;
  }

  /**
   * Devuelve los productos activos de un propietario paginados.
   *
   * @param ownerId identificador del propietario de los productos
   * @param pageable parámetros de paginación
   * @return página de DTOs con los productos activos del propietario indicado
   */
  public PagedResponse<UserProductResponse> findByOwner(UUID ownerId, Pageable pageable) {
    Page<UserProduct> page = userProductRepository.findByOwnerIdAndIsActiveTrue(ownerId, pageable);
    return toPagedResponse(page);
  }

  /**
   * Devuelve los productos activos de un propietario paginados y filtrados por categoría.
   *
   * @param ownerId identificador del propietario de los productos
   * @param pageable parámetros de paginación
   * @param categoryId identificador público de la categoría por la que filtrar
   * @return página de DTOs con los productos activos del propietario y categoría indicados; vacía
   *     si la categoría no existe
   */
  public PagedResponse<UserProductResponse> findByOwner(
      UUID ownerId, Pageable pageable, UUID categoryId) {
    @Nullable Long internal =
        categoryRepository.findByPublicId(categoryId).map(Category::getId).orElse(null);
    if (internal == null) {
      return new PagedResponse<>(List.of(), pageable.getPageNumber(), pageable.getPageSize(), 0);
    }
    Page<UserProduct> page =
        userProductRepository.findByOwnerIdAndIsActiveTrueAndCategoryId(
            ownerId, internal, pageable);
    return toPagedResponse(page);
  }

  /**
   * Busca un producto de usuario por su identificador público.
   *
   * @param publicId identificador público del producto a recuperar
   * @return DTO del producto encontrado
   * @throws BusinessException con ErrorCode.USER_PRODUCT_NOT_FOUND si el producto no existe o está
   *     inactivo
   */
  public UserProductResponse findById(UUID publicId) {
    UserProduct product =
        userProductRepository
            .findByPublicId(publicId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_PRODUCT_NOT_FOUND));
    if (!product.getIsActive()) {
      throw new BusinessException(ErrorCode.USER_PRODUCT_NOT_FOUND);
    }
    return UserProductResponse.from(
        product,
        resolveCategoryPublicId(product.getCategoryId()),
        resolveBasePublicId(product.getBasedOnBaseId()));
  }

  /**
   * Convierte una página de entidades {@link UserProduct} en un {@link PagedResponse} de DTOs,
   * resolviendo en lote los identificadores públicos de categorías y productos base.
   *
   * @param page página de entidades devuelta por el repositorio
   * @return envoltorio con los DTOs y los metadatos de paginación
   */
  private PagedResponse<UserProductResponse> toPagedResponse(Page<UserProduct> page) {
    List<UserProduct> content = page.getContent();
    Map<Long, UUID> categoryPublicIds = resolveCategoryPublicIds(content);
    Map<Long, UUID> basePublicIds = resolveBasePublicIds(content);
    List<UserProductResponse> responses =
        content.stream()
            .map(
                p ->
                    UserProductResponse.from(
                        p,
                        p.getCategoryId() == null ? null : categoryPublicIds.get(p.getCategoryId()),
                        p.getBasedOnBaseId() == null
                            ? null
                            : basePublicIds.get(p.getBasedOnBaseId())))
            .toList();
    return new PagedResponse<>(
        responses, page.getNumber(), page.getSize(), page.getTotalElements());
  }

  /**
   * Resuelve en lote el identificador público de las categorías referenciadas por los productos.
   *
   * @param content productos de la página de los que se leen las referencias a categoría
   * @return mapa de identificador interno a identificador público; vacío si no hay referencias que
   *     resolver
   */
  private Map<Long, UUID> resolveCategoryPublicIds(List<UserProduct> content) {
    Set<Long> ids =
        content.stream()
            .map(UserProduct::getCategoryId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    if (ids.isEmpty()) {
      return Map.of();
    }
    return categoryRepository.findAllById(ids).stream()
        .collect(Collectors.toMap(Category::getId, Category::getPublicId));
  }

  /**
   * Resuelve en lote el identificador público de los productos base referenciados por los
   * productos.
   *
   * @param content productos de la página de los que se leen las referencias a producto base
   * @return mapa de identificador interno a identificador público; vacío si no hay referencias que
   *     resolver
   */
  private Map<Long, UUID> resolveBasePublicIds(List<UserProduct> content) {
    Set<Long> ids =
        content.stream()
            .map(UserProduct::getBasedOnBaseId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    if (ids.isEmpty()) {
      return Map.of();
    }
    return baseProductRepository.findAllById(ids).stream()
        .collect(Collectors.toMap(BaseProduct::getId, BaseProduct::getPublicId));
  }

  /**
   * Resuelve el identificador público de la categoría con el identificador interno indicado.
   *
   * @param categoryId identificador interno de la categoría; puede ser {@code null}
   * @return identificador público de la categoría, o {@code null} si no hay referencia o ya no
   *     existe
   */
  private @Nullable UUID resolveCategoryPublicId(@Nullable Long categoryId) {
    if (categoryId == null) {
      return null;
    }
    return categoryRepository.findById(categoryId).map(Category::getPublicId).orElse(null);
  }

  /**
   * Resuelve el identificador público del producto base con el identificador interno indicado.
   *
   * @param basedOnBaseId identificador interno del producto base; puede ser {@code null}
   * @return identificador público del producto base, o {@code null} si no hay referencia o ya no
   *     existe
   */
  private @Nullable UUID resolveBasePublicId(@Nullable Long basedOnBaseId) {
    if (basedOnBaseId == null) {
      return null;
    }
    return baseProductRepository.findById(basedOnBaseId).map(BaseProduct::getPublicId).orElse(null);
  }

  /**
   * Crea un producto de usuario, copiando del producto base los campos ausentes del body cuando
   * {@code basedOnBaseId} está presente.
   *
   * <p>Si {@code basedOnBaseId} se indica, {@code name} y {@code description} se resuelven desde el
   * producto base en el locale de la petición y el resto de campos ausentes se copian del snapshot;
   * los valores del body prevalecen sobre los copiados. Sin {@code basedOnBaseId}, {@code name},
   * {@code defaultUnit} y {@code caloriesPer} son obligatorios. {@code basedOnBaseId} se persiste
   * como trazabilidad inmutable del producto de origen.
   *
   * @param request petición con los datos del producto de usuario
   * @param locale idioma en el que se resuelven el nombre y la descripción del producto base
   * @return DTO del producto de usuario recién creado
   * @throws BusinessException con ErrorCode.INVALID_BASE_PRODUCT si el producto base indicado no
   *     existe
   * @throws BusinessException con ErrorCode.NAME_REQUIRED si falta el nombre sin producto base
   * @throws BusinessException con ErrorCode.INVALID_CATEGORY si la categoría indicada no existe
   * @throws BusinessException con ErrorCode.DEFAULT_UNIT_REQUIRED si falta la unidad por defecto
   *     sin producto base
   * @throws BusinessException con ErrorCode.CALORIES_PER_REQUIRED si falta el {@code caloriesPer}
   *     sin producto base
   */
  @Transactional
  public UserProductResponse create(CreateUserProductRequest request, Locale locale) {
    @Nullable UUID basedOnBasePublicId = request.basedOnBaseId();
    @Nullable BaseProduct base = null;
    if (basedOnBasePublicId != null) {
      base =
          baseProductRepository
              .findByPublicId(basedOnBasePublicId)
              .orElseThrow(
                  () ->
                      new BusinessException(
                          ErrorCode.INVALID_BASE_PRODUCT,
                          "Base product with public id " + basedOnBasePublicId + " not found"));
    }

    @Nullable String name = request.name();
    if (name == null || name.isBlank()) {
      if (base != null) {
        name = baseProductService.resolveName(base, locale);
      }
      if (name == null || name.isBlank()) {
        throw new BusinessException(ErrorCode.NAME_REQUIRED);
      }
    }

    @Nullable String description = request.description();
    if (description == null && base != null) {
      description = baseProductService.resolveDescription(base, locale);
    }

    @Nullable Long categoryId = null;
    @Nullable UUID categoryPublicId = null;
    @Nullable UUID requestCategoryId = request.categoryId();
    if (requestCategoryId != null) {
      Category category =
          categoryRepository
              .findByPublicId(requestCategoryId)
              .orElseThrow(
                  () ->
                      new BusinessException(
                          ErrorCode.INVALID_CATEGORY,
                          "Category with public id " + requestCategoryId + " not found"));
      categoryId = Objects.requireNonNull(category.getId());
      categoryPublicId = category.getPublicId();
    } else if (base != null) {
      categoryId = base.getCategoryId();
      categoryPublicId = resolveCategoryPublicId(categoryId);
    }

    @Nullable UnitEnum defaultUnit = request.defaultUnit();
    if (defaultUnit == null && base != null) {
      defaultUnit = base.getDefaultUnit();
    }
    if (defaultUnit == null) {
      throw new BusinessException(ErrorCode.DEFAULT_UNIT_REQUIRED);
    }

    @Nullable Integer calories = request.calories();
    if (calories == null && base != null) {
      calories = base.getCalories();
    }

    @Nullable CaloriesPerEnum caloriesPer = request.caloriesPer();
    if (caloriesPer == null && base != null) {
      caloriesPer = base.getCaloriesPer();
    }
    if (caloriesPer == null) {
      throw new BusinessException(ErrorCode.CALORIES_PER_REQUIRED);
    }

    @Nullable Long basedOnBaseInternalId =
        base != null ? Objects.requireNonNull(base.getId()) : null;

    UserProduct product = new UserProduct();
    product.setOwnerId(request.ownerId());
    product.setName(name);
    product.setDescription(description);
    product.setCategoryId(categoryId);
    product.setBasedOnBaseId(basedOnBaseInternalId);
    product.setDefaultUnit(defaultUnit);
    product.setCalories(calories);
    product.setCaloriesPer(caloriesPer);
    @Nullable Boolean shareWithListMembers = request.shareWithListMembers();
    @Nullable Boolean shareWithFriends = request.shareWithFriends();
    product.setShareWithListMembers(shareWithListMembers != null ? shareWithListMembers : false);
    product.setShareWithFriends(shareWithFriends != null ? shareWithFriends : false);
    product.setIsActive(true);

    UserProduct saved = userProductRepository.save(product);
    @Nullable UUID basedOnBasePublicIdResponse = base != null ? base.getPublicId() : null;
    return UserProductResponse.from(saved, categoryPublicId, basedOnBasePublicIdResponse);
  }

  /**
   * Edita parcialmente un producto de usuario aplicando solo los campos no nulos del request.
   *
   * <p>Busca el producto por su identificador sin filtrar por estado, por lo que un propietario
   * puede editar también productos inactivos (por ejemplo para reactivarlos con {@code
   * isActive=true}). El {@code ownerId} del body se usa únicamente como verificación de propiedad y
   * no modifica el almacenado. {@code basedOnBaseId} es una traza inmutable que se ignora en
   * silencio si se envía.
   *
   * @param publicId identificador público del producto de usuario a editar
   * @param request petición con los campos a modificar; solo los no nulos se aplican
   * @return DTO del producto de usuario tras aplicar los cambios
   * @throws BusinessException con ErrorCode.USER_PRODUCT_NOT_FOUND si el producto no existe
   * @throws BusinessException con ErrorCode.OWNER_MISMATCH si el {@code ownerId} del request no
   *     coincide con el propietario almacenado
   * @throws BusinessException con ErrorCode.INVALID_CATEGORY si la categoría indicada no existe
   */
  @Transactional
  public UserProductResponse update(UUID publicId, UpdateUserProductRequest request) {
    UserProduct product =
        userProductRepository
            .findByPublicId(publicId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_PRODUCT_NOT_FOUND));

    if (!product.getOwnerId().equals(request.ownerId())) {
      throw new BusinessException(ErrorCode.OWNER_MISMATCH);
    }

    @Nullable String name = request.name();
    if (name != null) {
      product.setName(name);
    }

    @Nullable String description = request.description();
    if (description != null) {
      product.setDescription(description);
    }

    @Nullable UUID categoryId = request.categoryId();
    if (categoryId != null) {
      Category category =
          categoryRepository
              .findByPublicId(categoryId)
              .orElseThrow(
                  () ->
                      new BusinessException(
                          ErrorCode.INVALID_CATEGORY,
                          "Category with public id " + categoryId + " not found"));
      product.setCategoryId(Objects.requireNonNull(category.getId()));
    }

    @Nullable UnitEnum defaultUnit = request.defaultUnit();
    if (defaultUnit != null) {
      product.setDefaultUnit(defaultUnit);
    }

    @Nullable Integer calories = request.calories();
    if (calories != null) {
      product.setCalories(calories);
    }

    @Nullable CaloriesPerEnum caloriesPer = request.caloriesPer();
    if (caloriesPer != null) {
      product.setCaloriesPer(caloriesPer);
    }

    @Nullable Boolean shareWithListMembers = request.shareWithListMembers();
    if (shareWithListMembers != null) {
      product.setShareWithListMembers(shareWithListMembers);
    }

    @Nullable Boolean shareWithFriends = request.shareWithFriends();
    if (shareWithFriends != null) {
      product.setShareWithFriends(shareWithFriends);
    }

    @Nullable Boolean isActive = request.isActive();
    if (isActive != null) {
      product.setIsActive(isActive);
    }

    UserProduct saved = userProductRepository.save(product);
    return UserProductResponse.from(
        saved,
        resolveCategoryPublicId(saved.getCategoryId()),
        resolveBasePublicId(saved.getBasedOnBaseId()));
  }

  /**
   * Elimina físicamente un producto de usuario tras verificar la propiedad del {@code ownerId}.
   *
   * <p>Busca el producto por su identificador público sin filtrar por estado, por lo que un
   * propietario puede borrar también productos inactivos.
   *
   * @param publicId identificador público del producto de usuario a eliminar
   * @param ownerId identificador del propietario que solicita el borrado
   * @throws BusinessException con ErrorCode.USER_PRODUCT_NOT_FOUND si el producto no existe
   * @throws BusinessException con ErrorCode.OWNER_MISMATCH si el {@code ownerId} no coincide con el
   *     propietario almacenado
   */
  @Transactional
  public void delete(UUID publicId, UUID ownerId) {
    UserProduct product =
        userProductRepository
            .findByPublicId(publicId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_PRODUCT_NOT_FOUND));
    if (!product.getOwnerId().equals(ownerId)) {
      throw new BusinessException(ErrorCode.OWNER_MISMATCH);
    }
    userProductRepository.delete(product);
  }
}
