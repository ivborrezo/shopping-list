package dev.ivborrezo.shoppinglist.testsupport;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import java.util.List;
import java.util.UUID;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Utilidades de autenticación para {@code MockMvc} en los servicios servlet.
 *
 * <p>Inyectan un {@code Jwt} con el sujeto del usuario, el claim de roles y las autoridades
 * esperadas por la cadena de seguridad, sin pasar por el {@code JwtDecoder} real.
 */
public final class TestAuth {

  private TestAuth() {}

  /**
   * Autentica la petición como el usuario indicado con rol {@code USER}.
   *
   * @param userId identificador del usuario que se inyecta como sujeto del JWT
   * @return post-procesador de MockMvc con la autenticación
   */
  public static RequestPostProcessor user(UUID userId) {
    return jwt()
        .jwt(jwt -> jwt.subject(userId.toString()).claim("roles", List.of("USER")))
        .authorities(new SimpleGrantedAuthority("ROLE_USER"));
  }

  /** Autentica la petición como {@link TestUsers#USER_1}. */
  public static RequestPostProcessor user1() {
    return user(TestUsers.USER_1);
  }

  /** Autentica la petición como {@link TestUsers#USER_2}. */
  public static RequestPostProcessor user2() {
    return user(TestUsers.USER_2);
  }

  /** Autentica la petición como {@link TestUsers#ADMIN}. */
  public static RequestPostProcessor admin() {
    return admin(TestUsers.ADMIN);
  }

  /**
   * Autentica la petición como el administrador indicado con roles {@code USER} y {@code ADMIN}.
   *
   * @param userId identificador del administrador que se inyecta como sujeto del JWT
   * @return post-procesador de MockMvc con la autenticación de administrador
   */
  public static RequestPostProcessor admin(UUID userId) {
    return jwt()
        .jwt(jwt -> jwt.subject(userId.toString()).claim("roles", List.of("USER", "ADMIN")))
        .authorities(
            new SimpleGrantedAuthority("ROLE_USER"), new SimpleGrantedAuthority("ROLE_ADMIN"));
  }
}
