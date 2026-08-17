package dev.ivborrezo.shoppinglist.product.service.common;

/**
 * Excepción de negocio que lanza la capa de servicio para los errores previstos del contrato.
 *
 * <p>El manejador global la traduce a un {@code ProblemDetail} con el código y el detalle
 * indicados; {@code detail} es el mensaje específico en inglés.
 */
public class BusinessException extends RuntimeException {

  private final ErrorCode errorCode;

  private final String detail;

  /**
   * Construye una excepción de negocio con el título del código como detalle.
   *
   * @param errorCode código del catálogo que identifica el error
   */
  public BusinessException(ErrorCode errorCode) {
    this(errorCode, errorCode.getTitle());
  }

  /**
   * Construye una excepción de negocio con un detalle específico.
   *
   * @param errorCode código del catálogo que identifica el error
   * @param detail mensaje específico en inglés del error
   */
  public BusinessException(ErrorCode errorCode, String detail) {
    super(detail);
    this.errorCode = errorCode;
    this.detail = detail;
  }

  /**
   * Devuelve el código del catálogo que identifica el error.
   *
   * @return código del error
   */
  public ErrorCode getErrorCode() {
    return errorCode;
  }

  /**
   * Devuelve el mensaje específico en inglés del error.
   *
   * @return detalle del error
   */
  public String getDetail() {
    return detail;
  }
}
