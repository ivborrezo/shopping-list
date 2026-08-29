package dev.ivborrezo.shoppinglist.product.service.common;

/**
 * Catálogo de errores del contrato de la API, contrastable con el {@code api-contract.yaml} (ver
 * ADR-014).
 *
 * <p>Cada valor fija el HTTP status y el título estable en inglés del error; el {@code code}
 * serializado en el {@code ProblemDetail} es el nombre de la constante.
 *
 * <p>{@code VALIDATION_FAILED} es un valor reservado de nivel superior que solo usa el manejador de
 * validación: nunca se lanza desde un servicio.
 */
public enum ErrorCode {
  CATEGORY_NOT_FOUND(404, "Category not found"),
  INVALID_CATEGORY(400, "Invalid category"),
  BASE_PRODUCT_NOT_FOUND(404, "Base product not found"),
  USER_PRODUCT_NOT_FOUND(404, "User product not found"),
  PRODUCT_NOT_FOUND(404, "Product not found"),
  DUPLICATE_PRODUCT_CODE(409, "Duplicate product code"),
  DUPLICATE_CATEGORY_CODE(409, "Duplicate category code"),
  UNSUPPORTED_LOCALE(400, "Unsupported locale"),
  NAME_REQUIRED(400, "Name is required"),
  DEFAULT_UNIT_REQUIRED(400, "Default unit is required"),
  CALORIES_PER_REQUIRED(400, "Calories per is required"),
  OWNER_MISMATCH(403, "Owner mismatch"),
  INVALID_PRODUCT_TYPE(400, "Invalid product type"),
  INVALID_BASE_PRODUCT(400, "Invalid base product"),
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
