package dev.ivborrezo.shoppinglist.list.service.common;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Punto único de mapeo excepción → {@code ProblemDetail} del servicio (ADR-014).
 *
 * <p>Traduce la {@link BusinessException} (con su {@link ErrorCode}), los errores de validación
 * Bean Validation (shape con {@code errors} por campo) y las excepciones no controladas (500
 * genérico con {@code INTERNAL_ERROR}). Extiende {@link ResponseEntityExceptionHandler} para
 * conservar el status de las excepciones estándar de Spring MVC.
 *
 * <p>Los {@code 400} por parámetros problemáticos (tipo no convertible, parámetro obligatorio
 * ausente, body no procesable o validación de constraints) emiten siempre {@code
 * code=VALIDATION_FAILED} con el array {@code errors} cuando aplica.
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
    ProblemDetail problemDetail = validationProblemDetail(request);
    List<FieldErrorItem> errors =
        ex.getBindingResult().getFieldErrors().stream()
            .map(fe -> new FieldErrorItem(fe.getCode(), fe.getField(), fe.getDefaultMessage()))
            .toList();
    problemDetail.setProperty("errors", errors);
    return validationResponse(problemDetail);
  }

  /**
   * Traduce un fallo de conversión de un parámetro de la petición (path variable o query param) al
   * {@code ProblemDetail} de validación con una entrada {@code typeMismatch}.
   *
   * @param ex excepción de conversión de tipo lanzada al resolver el argumento del handler
   * @param headers cabeceras de la respuesta
   * @param status status de la respuesta
   * @param request petición web que produjo el error
   * @return respuesta con el {@code ProblemDetail} de validación
   */
  @Override
  protected ResponseEntity<Object> handleTypeMismatch(
      TypeMismatchException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
    ProblemDetail problemDetail = validationProblemDetail(request);
    String field =
        ex instanceof MethodArgumentTypeMismatchException mismatch
            ? mismatch.getName()
            : ex.getPropertyName();
    String message = "Value '" + ex.getValue() + "' is not valid for parameter '" + field + "'";
    problemDetail.setProperty(
        "errors", List.of(new FieldErrorItem("typeMismatch", field, message)));
    return validationResponse(problemDetail);
  }

  /**
   * Traduce la ausencia de un parámetro de petición obligatorio al {@code ProblemDetail} de
   * validación con una entrada {@code missing}.
   *
   * @param ex excepción lanzada cuando falta un parámetro obligatorio
   * @param headers cabeceras de la respuesta
   * @param status status de la respuesta
   * @param request petición web que produjo el error
   * @return respuesta con el {@code ProblemDetail} de validación
   */
  @Override
  protected ResponseEntity<Object> handleMissingServletRequestParameter(
      MissingServletRequestParameterException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    ProblemDetail problemDetail = validationProblemDetail(request);
    String message = "Required parameter '" + ex.getParameterName() + "' is missing";
    problemDetail.setProperty(
        "errors", List.of(new FieldErrorItem("missing", ex.getParameterName(), message)));
    return validationResponse(problemDetail);
  }

  /**
   * Traduce un fallo de validación de un parámetro de método al {@code ProblemDetail} de validación
   * con una entrada por cada error resoluble.
   *
   * @param ex excepción de validación de parámetros de método lanzada por Spring MVC
   * @param headers cabeceras de la respuesta
   * @param status status de la respuesta
   * @param request petición web que produjo el error
   * @return respuesta con el {@code ProblemDetail} de validación
   */
  @Override
  protected ResponseEntity<Object> handleHandlerMethodValidationException(
      HandlerMethodValidationException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    ProblemDetail problemDetail = validationProblemDetail(request);
    List<FieldErrorItem> errors =
        ex.getParameterValidationResults().stream()
            .flatMap(
                result ->
                    result.getResolvableErrors().stream()
                        .map(
                            error ->
                                new FieldErrorItem(
                                    lastCode(error),
                                    parameterName(result),
                                    error.getDefaultMessage())))
            .toList();
    problemDetail.setProperty("errors", errors);
    return validationResponse(problemDetail);
  }

  /**
   * Traduce un body no procesable al {@code ProblemDetail} de validación, sin array {@code errors}.
   *
   * @param ex excepción lanzada al no poder leer o convertir el body de la petición
   * @param headers cabeceras de la respuesta
   * @param status status de la respuesta
   * @param request petición web que produjo el error
   * @return respuesta con el {@code ProblemDetail} de validación
   */
  @Override
  protected ResponseEntity<Object> handleHttpMessageNotReadable(
      HttpMessageNotReadableException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    return validationResponse(validationProblemDetail(request));
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

  /**
   * Construye el {@code ProblemDetail} base de los errores de validación, reutilizado por todos los
   * handlers de parámetros problemáticos.
   *
   * @param request petición web que produjo el error
   * @return {@code ProblemDetail} 400 con título y código {@code VALIDATION_FAILED}
   */
  private ProblemDetail validationProblemDetail(WebRequest request) {
    ProblemDetail problemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, VALIDATION_TITLE);
    problemDetail.setTitle(VALIDATION_TITLE);
    problemDetail.setInstance(URI.create(request.getDescription(false).replaceFirst("^uri=", "")));
    problemDetail.setProperty("code", ErrorCode.VALIDATION_FAILED.name());
    return problemDetail;
  }

  /**
   * Envuelve el {@code ProblemDetail} de validación en la respuesta 400 con content-type {@code
   * application/problem+json}.
   *
   * @param problemDetail problema de validación ya construido
   * @return respuesta 400 lista para devolver al cliente
   */
  private ResponseEntity<Object> validationResponse(ProblemDetail problemDetail) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(problemDetail);
  }

  /**
   * Extrae el nombre del parámetro de método de un resultado de validación.
   *
   * @param result resultado de validación de un parámetro
   * @return nombre del parámetro, o cadena vacía si no está disponible
   */
  private String parameterName(ParameterValidationResult result) {
    String name = result.getMethodParameter().getParameterName();
    return name != null ? name : "";
  }

  /**
   * Extrae el código de constraint de un error resoluble, alineado con {@code
   * FieldError.getCode()}: el último de la lista de códigos.
   *
   * @param error error resoluble de la validación de parámetros
   * @return código de constraint, o {@code null} si no hay códigos
   */
  private @Nullable String lastCode(MessageSourceResolvable error) {
    String[] codes = error.getCodes();
    return codes != null && codes.length > 0 ? codes[codes.length - 1] : null;
  }

  /** Error de un campo de la petición del shape de validación Bean Validation. */
  private record FieldErrorItem(@Nullable String code, String field, @Nullable String message) {}
}
