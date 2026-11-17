package dev.ivborrezo.shoppinglist.gateway.error;

import dev.ivborrezo.shoppinglist.gateway.ratelimit.RateLimitExceededException;
import io.netty.channel.ConnectTimeoutException;
import io.netty.handler.timeout.ReadTimeoutException;
import io.netty.handler.timeout.TimeoutException;
import java.net.ConnectException;
import org.jspecify.annotations.Nullable;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.resource.NoResourceFoundException;
import org.springframework.web.server.ResponseStatusException;
import reactor.netty.http.client.PrematureCloseException;

/**
 * Traduce la excepción del borde a su código de error propio.
 *
 * <p>Recorre la cadena de causas y reconoce el tipo real de cada fallo: el rechazo del rate
 * limiting, las credenciales inválidas o ausentes, la falta de permisos, el 404 de enrutado por su
 * status, la saturación del almacén de sesiones, los fallos de transporte de Reactor Netty como
 * servicio no disponible y cualquier otra excepción como error interno. Una respuesta del destino,
 * incluidas sus 4xx/5xx, no pasa por este mapeo.
 */
@Component
public class BorderErrorMapper {

  private static final String MAX_SESSIONS_MESSAGE_PREFIX = "Max sessions limit reached";

  /**
   * Obtiene el código de error que corresponde a una excepción.
   *
   * @param error excepción que interrumpió la cadena del borde
   * @return código propio del borde
   */
  public ErrorCode map(Throwable error) {
    Throwable current = error;
    while (current != null) {
      ErrorCode code = mapCause(current);
      if (code != null) {
        return code;
      }
      current = nextCause(current);
    }
    return ErrorCode.INTERNAL_ERROR;
  }

  private @Nullable ErrorCode mapCause(Throwable error) {
    if (error instanceof RateLimitExceededException) {
      return ErrorCode.RATE_LIMIT_EXCEEDED;
    }
    if (error instanceof AuthenticationException || error instanceof JwtException) {
      return ErrorCode.UNAUTHENTICATED;
    }
    if (error instanceof AccessDeniedException) {
      return ErrorCode.ACCESS_DENIED;
    }
    if (isMaxSessionsLimit(error)) {
      return ErrorCode.SERVICE_UNAVAILABLE;
    }
    if (error instanceof NoResourceFoundException || isNotFound(error)) {
      return ErrorCode.ROUTE_NOT_FOUND;
    }
    if (isGatewayFailure(error) || isTransportFailure(error)) {
      return ErrorCode.SERVICE_UNAVAILABLE;
    }
    return null;
  }

  private boolean isMaxSessionsLimit(Throwable error) {
    return error instanceof IllegalStateException illegalState
        && illegalState.getMessage() != null
        && illegalState.getMessage().startsWith(MAX_SESSIONS_MESSAGE_PREFIX);
  }

  private boolean isGatewayFailure(Throwable error) {
    return error instanceof ResponseStatusException statusException
        && (statusException.getStatusCode().value() == HttpStatus.BAD_GATEWAY.value()
            || statusException.getStatusCode().value() == HttpStatus.GATEWAY_TIMEOUT.value());
  }

  private boolean isNotFound(Throwable error) {
    return error instanceof ResponseStatusException statusException
        && statusException.getStatusCode().value() == HttpStatus.NOT_FOUND.value();
  }

  private boolean isTransportFailure(Throwable error) {
    Throwable unwrapped = NestedExceptionUtils.getMostSpecificCause(error);
    return unwrapped instanceof ReadTimeoutException
        || unwrapped instanceof TimeoutException
        || unwrapped instanceof ConnectTimeoutException
        || unwrapped instanceof ConnectException
        || unwrapped instanceof PrematureCloseException;
  }

  private @Nullable Throwable nextCause(Throwable error) {
    Throwable cause = error.getCause();
    return cause == error ? null : cause;
  }
}
