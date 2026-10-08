package dev.ivborrezo.shoppinglist.list.service.common;

/**
 * Catálogo de errores del contrato de la API de listas, contrastable con el {@code
 * api-contract.yaml} (ver ADR-014).
 *
 * <p>Cada valor fija el HTTP status y el título estable en inglés del error; el {@code code}
 * serializado en el {@code ProblemDetail} es el nombre de la constante.
 *
 * <p>{@code VALIDATION_FAILED} es un valor reservado de nivel superior que solo usa el manejador de
 * validación: nunca se lanza desde un servicio.
 */
public enum ErrorCode {
  LIST_NOT_FOUND(404, "List not found"),
  LIST_ITEM_NOT_FOUND(404, "List item not found"),
  OWNER_MISMATCH(403, "Owner mismatch"),
  DUPLICATE_LIST_ITEM(409, "Duplicate list item"),
  INVALID_PRODUCT_REFERENCE(400, "Invalid product reference"),
  PRODUCT_SERVICE_UNAVAILABLE(503, "Product service unavailable"),
  INVALID_PRODUCT_TYPE(400, "Invalid product type"),
  VALIDATION_FAILED(400, "Validation failed");

  private final int httpStatus;

  private final String title;

  ErrorCode(int httpStatus, String title) {
    this.httpStatus = httpStatus;
    this.title = title;
  }

  /**
   * Devuelve el HTTP status asociado al error.
   *
   * @return código HTTP del error
   */
  public int getHttpStatus() {
    return httpStatus;
  }

  /**
   * Devuelve el título estable en inglés del error.
   *
   * @return título del error
   */
  public String getTitle() {
    return title;
  }
}
