package dev.ivborrezo.shoppinglist.gateway.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.web.server.csrf.CsrfException;

class BorderErrorMapperTest {

  private final BorderErrorMapper errorMapper = new BorderErrorMapper();

  @Test
  void authenticationException_isMappedToUnauthenticated() {
    ErrorCode code = errorMapper.map(new BadCredentialsException("Invalid credentials"));

    assertThat(code).isEqualTo(ErrorCode.UNAUTHENTICATED);
  }

  @Test
  void oauth2AuthenticationException_isMappedToUnauthenticated() {
    ErrorCode code =
        errorMapper.map(
            new OAuth2AuthenticationException(
                new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN, "Expired token", null)));

    assertThat(code).isEqualTo(ErrorCode.UNAUTHENTICATED);
  }

  @Test
  void jwtException_isMappedToUnauthenticated() {
    ErrorCode code = errorMapper.map(new JwtException("Cannot decode token"));

    assertThat(code).isEqualTo(ErrorCode.UNAUTHENTICATED);
  }

  @Test
  void nestedAuthenticationException_isMappedToUnauthenticated() {
    ErrorCode code = errorMapper.map(new RuntimeException(new JwtException("Invalid signature")));

    assertThat(code).isEqualTo(ErrorCode.UNAUTHENTICATED);
  }

  @Test
  void accessDeniedException_isMappedToAccessDenied() {
    ErrorCode code = errorMapper.map(new AccessDeniedException("Access denied"));

    assertThat(code).isEqualTo(ErrorCode.ACCESS_DENIED);
  }

  @Test
  void csrfException_isMappedToAccessDenied() {
    ErrorCode code = errorMapper.map(new CsrfException("CSRF token missing"));

    assertThat(code).isEqualTo(ErrorCode.ACCESS_DENIED);
  }

  @Test
  void maxSessionsLimit_isMappedToServiceUnavailable() {
    ErrorCode code =
        errorMapper.map(new IllegalStateException("Max sessions limit reached: 10000"));

    assertThat(code).isEqualTo(ErrorCode.SERVICE_UNAVAILABLE);
  }

  @Test
  void nestedMaxSessionsLimit_isMappedToServiceUnavailable() {
    ErrorCode code =
        errorMapper.map(
            new RuntimeException(new IllegalStateException("Max sessions limit reached: 2")));

    assertThat(code).isEqualTo(ErrorCode.SERVICE_UNAVAILABLE);
  }

  @Test
  void unrelatedIllegalStateException_isMappedToInternalError() {
    ErrorCode code = errorMapper.map(new IllegalStateException("boom"));

    assertThat(code).isEqualTo(ErrorCode.INTERNAL_ERROR);
  }
}
