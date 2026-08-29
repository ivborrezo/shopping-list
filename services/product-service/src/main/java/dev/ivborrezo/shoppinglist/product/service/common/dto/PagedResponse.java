package dev.ivborrezo.shoppinglist.product.service.common.dto;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * Envoltorio genérico de una página de resultados, compatible con {@code Page<T>} de Spring Data.
 *
 * <p>Desacopla el contrato público de la API de la dependencia interna del framework: los campos
 * expuestos ({@code content}, {@code page}, {@code size}, {@code totalElements}) son los declarados
 * en el {@code api-contract.yaml} del servicio, sin exponer {@code totalPages}, {@code sort} ni
 * {@code pageable}.
 *
 * @param <T> tipo de los elementos de la página de contenido
 * @param content lista de elementos de la página actual
 * @param page número de página (0-indexed)
 * @param size tamaño de página solicitado
 * @param totalElements número total de elementos disponibles en todas las páginas
 */
public record PagedResponse<T>(List<T> content, int page, int size, long totalElements) {

  /**
   * Construye un {@code PagedResponse} a partir de una {@link Page} de Spring Data.
   *
   * @param <T> tipo de los elementos de la página
   * @param page resultado paginado de Spring Data del que se extraen los campos
   * @return envoltorio con los campos del contrato público
   */
  public static <T> PagedResponse<T> from(Page<T> page) {
    return new PagedResponse<>(
        page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements());
  }
}
