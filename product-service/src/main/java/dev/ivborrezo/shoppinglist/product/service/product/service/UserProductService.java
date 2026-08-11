package dev.ivborrezo.shoppinglist.product.service.product.service;

import dev.ivborrezo.shoppinglist.product.service.common.dto.PagedResponse;
import dev.ivborrezo.shoppinglist.product.service.product.dto.UserProductResponseDto;
import dev.ivborrezo.shoppinglist.product.service.product.entity.UserProduct;
import dev.ivborrezo.shoppinglist.product.service.product.repository.UserProductRepository;
import java.util.List;
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

  /**
   * Construye el servicio de productos de usuario con el repositorio correspondiente.
   *
   * @param userProductRepository repositorio de productos de usuario
   */
  public UserProductService(UserProductRepository userProductRepository) {
    this.userProductRepository = userProductRepository;
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
}
