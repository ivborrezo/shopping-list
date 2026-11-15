package dev.ivborrezo.shoppinglist.product.service.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ivborrezo.shoppinglist.product.service.common.security.SecurityJwtProperties;
import dev.ivborrezo.shoppinglist.testsupport.TestUsers;
import jakarta.servlet.FilterChain;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Test unitario de {@link UserIdMdcFilter}.
 *
 * <p>Verifica que el filtro publica el UUID del JWT en el MDC, que lo ignora cuando no hay
 * autenticación JWT o el claim no es un UUID válido, y que no altera el flujo de la cadena.
 */
class UserIdMdcFilterTest {

  private static final String MDC_KEY = "userId";

  private final SecurityJwtProperties properties = new SecurityJwtProperties();

  private final UserIdMdcFilter filter = new UserIdMdcFilter(properties);

  @AfterEach
  void tearDown() {
    MDC.clear();
    SecurityContextHolder.clearContext();
  }

  @Test
  void doFilter_withAuthenticatedJwt_putsUserIdInMdc() throws Exception {
    authenticate(jwt("sub", TestUsers.USER_1.toString()));
    String[] duringChain = new String[1];

    filter.doFilter(request(), new MockHttpServletResponse(), capture(duringChain));

    assertThat(duringChain[0]).isEqualTo(TestUsers.USER_1.toString());
  }

  @Test
  void doFilter_withCustomPrincipalClaim_readsConfiguredClaim() throws Exception {
    properties.setPrincipalClaim("user_id");
    authenticate(jwt("user_id", TestUsers.USER_2.toString()));
    String[] duringChain = new String[1];

    filter.doFilter(request(), new MockHttpServletResponse(), capture(duringChain));

    assertThat(duringChain[0]).isEqualTo(TestUsers.USER_2.toString());
  }

  @Test
  void doFilter_withoutAuthentication_doesNotPutUserId() throws Exception {
    String[] duringChain = new String[1];

    filter.doFilter(request(), new MockHttpServletResponse(), capture(duringChain));

    assertThat(duringChain[0]).isNull();
  }

  @Test
  void doFilter_withNonJwtPrincipal_doesNotPutUserId() throws Exception {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(TestUsers.USER_1.toString(), "n/a", List.of()));
    String[] duringChain = new String[1];

    filter.doFilter(request(), new MockHttpServletResponse(), capture(duringChain));

    assertThat(duringChain[0]).isNull();
  }

  @Test
  void doFilter_withNonUuidClaim_doesNotPutUserId() throws Exception {
    authenticate(jwt("sub", "not-a-uuid"));
    String[] duringChain = new String[1];

    filter.doFilter(request(), new MockHttpServletResponse(), capture(duringChain));

    assertThat(duringChain[0]).isNull();
  }

  private void authenticate(Jwt jwt) {
    SecurityContextHolder.getContext()
        .setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
  }

  private Jwt jwt(String claim, Object value) {
    return Jwt.withTokenValue("token").header("alg", "RS256").claim(claim, value).build();
  }

  private MockHttpServletRequest request() {
    return new MockHttpServletRequest();
  }

  private FilterChain capture(String[] target) {
    return (request, response) -> target[0] = MDC.get(MDC_KEY);
  }
}
