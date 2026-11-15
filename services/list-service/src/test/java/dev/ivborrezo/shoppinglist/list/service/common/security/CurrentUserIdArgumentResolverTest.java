package dev.ivborrezo.shoppinglist.list.service.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ivborrezo.shoppinglist.testsupport.TestUsers;
import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Test unitario de {@link CurrentUserIdArgumentResolver}.
 *
 * <p>Verifica el reconocimiento del parámetro anotado, la extracción del claim configurado del JWT,
 * el respaldo por nombre de autenticación y el rechazo cuando no hay usuario autenticado.
 */
class CurrentUserIdArgumentResolverTest {

  private final SecurityJwtProperties properties = new SecurityJwtProperties();

  private final CurrentUserIdArgumentResolver resolver =
      new CurrentUserIdArgumentResolver(properties);

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void supportsParameter_withCurrentUserIdUuid_returnsTrue() throws Exception {
    assertThat(resolver.supportsParameter(parameter("currentUserId", UUID.class))).isTrue();
  }

  @Test
  void supportsParameter_withoutAnnotation_returnsFalse() throws Exception {
    assertThat(resolver.supportsParameter(parameter("plain", UUID.class))).isFalse();
  }

  @Test
  void resolveArgument_withJwtPrincipal_readsConfiguredClaim() throws Exception {
    Jwt jwt =
        Jwt.withTokenValue("token")
            .header("alg", "RS256")
            .subject(TestUsers.USER_1.toString())
            .claim("sub", TestUsers.USER_1.toString())
            .build();
    SecurityContextHolder.getContext()
        .setAuthentication(new JwtAuthenticationToken(jwt, List.of()));

    Object resolved =
        resolver.resolveArgument(parameter("currentUserId", UUID.class), null, null, null);

    assertThat(resolved).isEqualTo(TestUsers.USER_1);
  }

  @Test
  void resolveArgument_withCustomPrincipalClaim_readsConfiguredClaim() throws Exception {
    properties.setPrincipalClaim("user_id");
    Jwt jwt =
        Jwt.withTokenValue("token")
            .header("alg", "RS256")
            .claim("user_id", TestUsers.USER_2.toString())
            .build();
    SecurityContextHolder.getContext()
        .setAuthentication(new JwtAuthenticationToken(jwt, List.of()));

    Object resolved =
        resolver.resolveArgument(parameter("currentUserId", UUID.class), null, null, null);

    assertThat(resolved).isEqualTo(TestUsers.USER_2);
  }

  @Test
  void resolveArgument_withNonJwtPrincipal_fallsBackToAuthenticationName() throws Exception {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(TestUsers.ADMIN.toString(), "n/a", List.of()));

    Object resolved =
        resolver.resolveArgument(parameter("currentUserId", UUID.class), null, null, null);

    assertThat(resolved).isEqualTo(TestUsers.ADMIN);
  }

  @Test
  void resolveArgument_withoutAuthentication_throwsCredentialsNotFound() throws Exception {
    MethodParameter parameter = parameter("currentUserId", UUID.class);

    assertThatThrownBy(() -> resolver.resolveArgument(parameter, null, null, null))
        .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
  }

  private MethodParameter parameter(String methodName, Class<?>... parameterTypes)
      throws NoSuchMethodException {
    Method method = SampleController.class.getDeclaredMethod(methodName, parameterTypes);
    return new MethodParameter(method, 0);
  }

  /** Controller de prueba cuyos parámetros ejercitan el resolver. */
  static class SampleController {

    void currentUserId(@CurrentUserId UUID userId) {}

    void plain(UUID userId) {}
  }
}
