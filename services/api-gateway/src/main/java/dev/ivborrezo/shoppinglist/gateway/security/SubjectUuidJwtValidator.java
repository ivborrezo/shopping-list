package dev.ivborrezo.shoppinglist.gateway.security;

import java.util.UUID;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Valida que el JWT incluya en el claim configurado un identificador de usuario con formato UUID.
 *
 * <p>Cualquier token sin el claim, con el claim en blanco o con un valor no convertible a UUID se
 * rechaza con {@code invalid_token}, de modo que el punto de entrada responda {@code 401
 * UNAUTHENTICATED}. El mismo validador se aplica al canal {@code Bearer} y al {@code id_token} del
 * canal de sesión.
 */
public class SubjectUuidJwtValidator implements OAuth2TokenValidator<Jwt> {

  private final String principalClaim;

  /**
   * Construye el validador para el claim de identificador de usuario indicado.
   *
   * @param principalClaim nombre del claim que transporta el UUID del usuario
   */
  public SubjectUuidJwtValidator(String principalClaim) {
    this.principalClaim = principalClaim;
  }

  @Override
  public OAuth2TokenValidatorResult validate(Jwt token) {
    String subject = token.getClaimAsString(principalClaim);
    if (subject == null || subject.isBlank()) {
      return invalid("Missing or blank '" + principalClaim + "' claim");
    }
    try {
      UUID.fromString(subject);
    } catch (IllegalArgumentException exception) {
      return invalid("Claim '" + principalClaim + "' is not a valid UUID");
    }
    return OAuth2TokenValidatorResult.success();
  }

  private OAuth2TokenValidatorResult invalid(String description) {
    return OAuth2TokenValidatorResult.failure(
        new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN, description, null));
  }
}
