package dev.ivborrezo.shoppinglist.testsupport;

import java.util.UUID;

/**
 * Identidades deterministas y emisor compartidos por las utilidades de test de los servicios.
 *
 * <p>Los valores fijos permiten escribir aserciones legibles y reutilizar los mismos
 * identificadores en los distintos servicios y en el realm local de pruebas.
 */
public final class TestUsers {

  /** Usuario estándar propietario de los recursos de los tests. */
  public static final UUID USER_1 = UUID.fromString("11111111-1111-4111-8111-111111111111");

  /** Segundo usuario estándar, usado para verificar el aislamiento por propietario. */
  public static final UUID USER_2 = UUID.fromString("22222222-2222-4222-8222-222222222222");

  /** Usuario con rol de administrador del catálogo global. */
  public static final UUID ADMIN = UUID.fromString("99999999-9999-4999-8999-999999999999");

  /** Emisor OIDC de los tokens firmados en los tests. */
  public static final String TEST_ISSUER = "http://test-oidc.local/realms/shopping-list-test";

  private TestUsers() {}
}
