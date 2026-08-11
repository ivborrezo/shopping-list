package dev.ivborrezo.shoppinglist.product.service.product.service;

import dev.ivborrezo.shoppinglist.product.service.category.repository.CategoryRepository;
import dev.ivborrezo.shoppinglist.product.service.common.CaloriesPerEnum;
import dev.ivborrezo.shoppinglist.product.service.common.UnitEnum;
import dev.ivborrezo.shoppinglist.product.service.common.dto.PagedResponse;
import dev.ivborrezo.shoppinglist.product.service.product.dto.CreateUserProductRequest;
import dev.ivborrezo.shoppinglist.product.service.product.dto.UpdateUserProductRequest;
import dev.ivborrezo.shoppinglist.product.service.product.dto.UserProductResponseDto;
import dev.ivborrezo.shoppinglist.product.service.product.entity.BaseProduct;
import dev.ivborrezo.shoppinglist.product.service.product.entity.UserProduct;
import dev.ivborrezo.shoppinglist.product.service.product.repository.BaseProductRepository;
import dev.ivborrezo.shoppinglist.product.service.product.repository.UserProductRepository;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

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
  public PagedResponse<UserProductResponseDto> findByOwner(UUID ownerId, Pageable pageable) {
    Page<UserProduct> page = userProductRepository.findByOwnerIdAndIsActiveTrue(ownerId, pageable);
    return toPagedResponse(page);
  }

  /**
   * Devuelve los productos activos de un propietario paginados y filtrados por categoría.
   *
   * @param ownerId identificador del propietario de los productos
   * @param pageable parámetros de paginación
   * @param categoryId identificador de la categoría por la que filtrar
   * @return página de DTOs con los productos activos del propietario y categoría indicados
   */
  public PagedResponse<UserProductResponseDto> findByOwner(
      UUID ownerId, Pageable pageable, Long categoryId) {
    Page<UserProduct> page =
        userProductRepository.findByOwnerIdAndIsActiveTrueAndCategoryId(
            ownerId, categoryId, pageable);
    return toPagedResponse(page);
  }

  /**
   * Busca un producto de usuario por su identificador.
   *
   * @param id identificador del producto a recuperar
   * @return DTO del producto encontrado
   * @throws ResponseStatusException con {@code 404} si el producto no existe o está inactivo
   */
  public UserProductResponseDto findById(Long id) {
    UserProduct product =
        userProductRepository
            .findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    if (!product.getIsActive()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
    return UserProductResponseDto.from(product);
  }

  /**
   * Convierte una página de entidades {@link UserProduct} en un {@link PagedResponse} de DTOs.
   *
   * @param page página de entidades devuelta por el repositorio
   * @return envoltorio con los DTOs y los metadatos de paginación
   */
  private PagedResponse<UserProductResponseDto> toPagedResponse(Page<UserProduct> page) {
    List<UserProductResponseDto> dtos =
        page.getContent().stream().map(UserProductResponseDto::from).toList();
    return new PagedResponse<>(dtos, page.getNumber(), page.getSize(), page.getTotalElements());
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
   * @throws ResponseStatusException con {@code 400} si el producto base indicado no existe, si
   *     falta algún campo obligatorio sin producto base, o si la categoría indicada no existe
   */
  @Transactional
  public UserProductResponseDto create(CreateUserProductRequest request, Locale locale) {
    BaseProduct base = null;
    if (request.basedOnBaseId() != null) {
      base =
          baseProductRepository
              .findById(request.basedOnBaseId())
              .orElseThrow(
                  () ->
                      new ResponseStatusException(
                          HttpStatus.BAD_REQUEST, "Base product not found"));
    }

    String name = request.name();
    if (name == null || name.isBlank()) {
      if (base != null) {
        name = baseProductService.resolveName(base, locale);
      }
      if (name == null || name.isBlank()) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name is required");
      }
    }

    String description = request.description();
    if (description == null && base != null) {
      description = baseProductService.resolveDescription(base, locale);
    }

    Long categoryId = request.categoryId();
    if (categoryId != null) {
      if (!categoryRepository.existsById(categoryId)) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Category not found");
      }
    } else if (base != null) {
      categoryId = base.getCategoryId();
    }

    UnitEnum defaultUnit =
        request.defaultUnit() != null ? request.defaultUnit() : base.getDefaultUnit();
    if (defaultUnit == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "defaultUnit is required");
    }

    Integer calories = request.calories();
    if (calories == null && base != null) {
      calories = base.getCalories();
    }

    CaloriesPerEnum caloriesPer =
        request.caloriesPer() != null ? request.caloriesPer() : base.getCaloriesPer();
    if (caloriesPer == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "caloriesPer is required");
    }

    UserProduct product = new UserProduct();
    product.setOwnerId(request.ownerId());
    product.setName(name);
    product.setDescription(description);
    product.setCategoryId(categoryId);
    product.setBasedOnBaseId(request.basedOnBaseId());
    product.setDefaultUnit(defaultUnit);
    product.setCalories(calories);
    product.setCaloriesPer(caloriesPer);
    product.setShareWithListMembers(
        request.shareWithListMembers() != null ? request.shareWithListMembers() : false);
    product.setShareWithFriends(
        request.shareWithFriends() != null ? request.shareWithFriends() : false);
    product.setIsActive(true);

    UserProduct saved = userProductRepository.save(product);
    return UserProductResponseDto.from(saved);
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
   * @param id identificador del producto de usuario a editar
   * @param request petición con los campos a modificar; solo los no nulos se aplican
   * @return DTO del producto de usuario tras aplicar los cambios
   * @throws ResponseStatusException con {@code 404} si el producto no existe
   * @throws ResponseStatusException con {@code 403} si el {@code ownerId} del request no coincide
   *     con el propietario almacenado
   * @throws ResponseStatusException con {@code 400} si la categoría indicada no existe
   */
  @Transactional
  public UserProductResponseDto update(Long id, UpdateUserProductRequest request) {
    UserProduct product =
        userProductRepository
            .findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

    if (!product.getOwnerId().equals(request.ownerId())) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }

    if (request.name() != null) {
      product.setName(request.name());
    }

    if (request.description() != null) {
      product.setDescription(request.description());
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

    if (request.shareWithListMembers() != null) {
      product.setShareWithListMembers(request.shareWithListMembers());
    }

    if (request.shareWithFriends() != null) {
      product.setShareWithFriends(request.shareWithFriends());
    }

    if (request.isActive() != null) {
      product.setIsActive(request.isActive());
    }

    UserProduct saved = userProductRepository.save(product);
    return UserProductResponseDto.from(saved);
  }

  /**
   * Elimina físicamente un producto de usuario tras verificar la propiedad del {@code ownerId}.
   *
   * <p>Busca el producto por su identificador sin filtrar por estado, por lo que un propietario
   * puede borrar también productos inactivos.
   *
   * @param id identificador del producto de usuario a eliminar
   * @param ownerId identificador del propietario que solicita el borrado
   * @throws ResponseStatusException con {@code 404} si el producto no existe
   * @throws ResponseStatusException con {@code 403} si el {@code ownerId} no coincide con el
   *     propietario almacenado
   */
  @Transactional
  public void delete(Long id, UUID ownerId) {
    UserProduct product =
        userProductRepository
            .findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    if (!product.getOwnerId().equals(ownerId)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }
    userProductRepository.delete(product);
  }
}
