package dev.ivborrezo.shoppinglist.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

class SubjectUuidJwtValidatorTest {

  private static final String USER_ID = "11111111-1111-4111-8111-111111111111";

  private final SubjectUuidJwtValidator validator = new SubjectUuidJwtValidator("sub");

  @Test
  void validUuidSubject_passes() {
    OAuth2TokenValidatorResult result = validator.validate(jwtWith("sub", USER_ID));

    assertThat(result.hasErrors()).isFalse();
  }

  @Test
  void missingSubject_fails() {
    OAuth2TokenValidatorResult result = validator.validate(jwtWithout("sub"));

    assertThat(result.hasErrors()).isTrue();
    assertThat(result.getErrors())
        .singleElement()
        .satisfies(error -> assertThat(error.getErrorCode()).isEqualTo("invalid_token"));
  }

  @Test
  void blankSubject_fails() {
    OAuth2TokenValidatorResult result = validator.validate(jwtWith("sub", " "));

    assertThat(result.hasErrors()).isTrue();
  }

  @Test
  void nonUuidSubject_fails() {
    OAuth2TokenValidatorResult result = validator.validate(jwtWith("sub", "user-1"));

    assertThat(result.hasErrors()).isTrue();
    assertThat(result.getErrors())
        .singleElement()
        .satisfies(error -> assertThat(error.getErrorCode()).isEqualTo("invalid_token"));
  }

  @Test
  void customPrincipalClaim_isValidated() {
    SubjectUuidJwtValidator customValidator = new SubjectUuidJwtValidator("user_id");

    assertThat(customValidator.validate(jwtWith("user_id", USER_ID)).hasErrors()).isFalse();
    assertThat(customValidator.validate(jwtWith("sub", USER_ID)).hasErrors()).isTrue();
  }

  private Jwt jwtWith(String claim, String value) {
    return Jwt.withTokenValue("token")
        .header("alg", "RS256")
        .issuedAt(Instant.now())
        .expiresAt(Instant.now().plusSeconds(60))
        .claim(claim, value)
        .build();
  }

  private Jwt jwtWithout(String claim) {
    return Jwt.withTokenValue("token")
        .header("alg", "RS256")
        .issuedAt(Instant.now())
        .expiresAt(Instant.now().plusSeconds(60))
        .claim("roles", java.util.List.of("USER"))
        .build();
  }
}
