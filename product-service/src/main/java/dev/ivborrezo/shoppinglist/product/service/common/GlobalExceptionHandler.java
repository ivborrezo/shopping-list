package dev.ivborrezo.shoppinglist.product.service.common;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Punto único de mapeo excepción → {@code ProblemDetail} del servicio (ADR-014).
 *
 * <p>Traduce la {@link BusinessException} (con su {@link ErrorCode}), los errores de validación
 * Bean Validation (shape con {@code errors} por campo) y las excepciones no controladas (500
 * genérico con {@code INTERNAL_ERROR}). Extiende {@link ResponseEntityExceptionHandler} para
 * conservar el status de las excepciones estándar de Spring MVC.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  private static final String INTERNAL_ERROR = "INTERNAL_ERROR";

  private static final String INTERNAL_ERROR_TITLE = "Internal Server Error";

  private static final String VALIDATION_TITLE = "Validation failed";

  /**
   * Traduce una {@link BusinessException} al {@code ProblemDetail} del contrato con su código y
   * detalle.
   *
   * @param ex excepción de negocio lanzada por la capa de servicio
   * @param request petición HTTP que produjo el error
   * @return respuesta con el {@code ProblemDetail} y el status del código del catálogo
   */
  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<ProblemDetail> handleBusinessException(
      BusinessException ex, HttpServletRequest request) {
    ErrorCode code = ex.getErrorCode();
    ProblemDetail problemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.valueOf(code.getHttpStatus()), ex.getDetail());
    problemDetail.setTitle(code.getTitle());
    problemDetail.setInstance(URI.create(request.getRequestURI()));
    problemDetail.setProperty("code", code.name());
    return ResponseEntity.status(code.getHttpStatus())
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(problemDetail);
  }

  /**
   * Traduce un fallo de validación Bean Validation al {@code ProblemDetail} con el código de nivel
   * superior {@code VALIDATION_FAILED} y el array {@code errors} por campo.
   *
   * @param ex excepción de validación lanzada por Spring MVC al fallar las constraints del body
   * @param headers cabeceras de la respuesta
   * @param status status de la respuesta
   * @param request petición web que produjo el error
   * @return respuesta con el {@code ProblemDetail} de validación
   */
  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    ProblemDetail problemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, VALIDATION_TITLE);
    problemDetail.setTitle(VALIDATION_TITLE);
    problemDetail.setInstance(URI.create(request.getDescription(false).replaceFirst("^uri=", "")));
    problemDetail.setProperty("code", ErrorCode.VALIDATION_FAILED.name());
    List<FieldErrorItem> errors =
        ex.getBindingResult().getFieldErrors().stream()
            .map(fe -> new FieldErrorItem(fe.getCode(), fe.getField(), fe.getDefaultMessage()))
            .toList();
    problemDetail.setProperty("errors", errors);
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(problemDetail);
  }

  /**
   * Traduce una excepción no controlada por el contrato a un {@code ProblemDetail} genérico 500 con
   * código {@code INTERNAL_ERROR}.
   *
   * @param ex excepción no controlada
   * @param request petición HTTP que produjo el error
   * @return respuesta con el {@code ProblemDetail} del fallback 500
   */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ProblemDetail> handleGenericException(
      Exception ex, HttpServletRequest request) {
    log.error("Unhandled exception", ex);
    ProblemDetail problemDetail =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
    problemDetail.setTitle(INTERNAL_ERROR_TITLE);
    problemDetail.setInstance(URI.create(request.getRequestURI()));
    problemDetail.setProperty("code", INTERNAL_ERROR);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(problemDetail);
  }

  /** Error de un campo de la petición del shape de validación Bean Validation. */
  private record FieldErrorItem(String code, String field, String message) {}
}
