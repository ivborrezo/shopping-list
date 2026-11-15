package dev.ivborrezo.shoppinglist.product.service.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ivborrezo.shoppinglist.testsupport.TestUsers;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Test unitario de {@link SubjectUuidJwtValidator}.
 *
 * <p>Cubre el sujeto UUID válido, el claim ausente o en blanco y el claim con un valor que no es un
 * UUID, así como la lectura de un claim de identificador distinto del estándar {@code sub}.
 */
class SubjectUuidJwtValidatorTest {

  @Test
  void validate_withUuidSubject_succeeds() {
    SubjectUuidJwtValidator validator = new SubjectUuidJwtValidator("sub");

    OAuth2TokenValidatorResult result = validator.validate(jwt("sub", TestUsers.USER_1.toString()));

    assertThat(result.hasErrors()).isFalse();
  }

  @Test
  void validate_withMissingSubject_failsWithInvalidToken() {
    SubjectUuidJwtValidator validator = new SubjectUuidJwtValidator("sub");

    OAuth2TokenValidatorResult result =
        validator.validate(jwt("other", TestUsers.USER_1.toString()));

    assertThat(result.hasErrors()).isTrue();
    assertThat(result.getErrors())
        .singleElement()
        .satisfies(
            error -> assertThat(error.getErrorCode()).isEqualTo(OAuth2ErrorCodes.INVALID_TOKEN));
  }

  @Test
  void validate_withBlankSubject_failsWithInvalidToken() {
    SubjectUuidJwtValidator validator = new SubjectUuidJwtValidator("sub");

    OAuth2TokenValidatorResult result = validator.validate(jwt("sub", " "));

    assertThat(result.hasErrors()).isTrue();
  }

  @Test
  void validate_withNonUuidSubject_failsWithInvalidToken() {
    SubjectUuidJwtValidator validator = new SubjectUuidJwtValidator("sub");

    OAuth2TokenValidatorResult result = validator.validate(jwt("sub", "not-a-uuid"));

    assertThat(result.hasErrors()).isTrue();
    assertThat(result.getErrors())
        .singleElement()
        .satisfies(
            error -> assertThat(error.getErrorCode()).isEqualTo(OAuth2ErrorCodes.INVALID_TOKEN));
  }

  @Test
  void validate_withCustomPrincipalClaim_readsConfiguredClaim() {
    SubjectUuidJwtValidator validator = new SubjectUuidJwtValidator("user_id");

    OAuth2TokenValidatorResult result =
        validator.validate(jwt("user_id", TestUsers.USER_2.toString()));

    assertThat(result.hasErrors()).isFalse();
  }

  private Jwt jwt(String claim, Object value) {
    return Jwt.withTokenValue("token").header("alg", "RS256").claim(claim, value).build();
  }
}
