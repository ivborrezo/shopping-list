package dev.ivborrezo.shoppinglist.gateway.error;

/**
 * Catálogo de errores propios del borde, contrastable con los contratos de la API.
 *
 * <p>Cada valor fija el HTTP status, el título estable en inglés y el detalle genérico que se
 * serializa en el {@code ProblemDetail}. El {@code code} es el nombre de la constante.
 */
public enum ErrorCode {
  ROUTE_NOT_FOUND(404, "Route not found", "No route matches the requested path"),
  SERVICE_UNAVAILABLE(
      503, "Service temporarily unavailable", "The upstream service is currently unavailable"),
  INTERNAL_ERROR(500, "Internal Server Error", "An unexpected error occurred");

  private final int httpStatus;

  private final String title;

  private final String detail;

  ErrorCode(int httpStatus, String title, String detail) {
    this.httpStatus = httpStatus;
    this.title = title;
    this.detail = detail;
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

  /**
   * Devuelve el detalle genérico del error, sin topología interna.
   *
   * @return detalle del error
   */
  public String getDetail() {
    return detail;
  }
}
