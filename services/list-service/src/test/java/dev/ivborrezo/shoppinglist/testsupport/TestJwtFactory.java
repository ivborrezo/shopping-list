package dev.ivborrezo.shoppinglist.testsupport;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Fábrica fluida de tokens JWT firmados para los tests de seguridad.
 *
 * <p>Permite fijar sujeto, emisor, expiración, audiencia, {@code nonce}, claim de roles y claims
 * adicionales, y firmar con el par RSA principal o con una clave alternativa para provocar errores
 * de validación de firma. También ofrece un token malformado para los escenarios de rechazo.
 */
public final class TestJwtFactory {

  private String subject = TestUsers.USER_1.toString();

  private String issuer = TestUsers.TEST_ISSUER;

  private Instant issuedAt = Instant.now();

  private Instant expiresAt = issuedAt.plusSeconds(300);

  private final List<String> audiences = new ArrayList<>();

  private final Map<String, Object> claims = new LinkedHashMap<>();

  private String rolesClaim = "roles";

  private List<String> roles = List.of("USER");

  private String nonce;

  private TestRsaKey key = TestRsaKey.primary();

  private TestJwtFactory() {}

  /**
   * Inicia la construcción de un token con los valores por defecto de los tests.
   *
   * @return fábrica de tokens
   */
  public static TestJwtFactory token() {
    return new TestJwtFactory();
  }

  /**
   * Devuelve un token sintácticamente inválido para los escenarios de rechazo.
   *
   * @return cadena que no es un JWT
   */
  public static String malformed() {
    return "not-a-valid-jwt";
  }

  /** Fija el sujeto del token a partir de un identificador de usuario. */
  public TestJwtFactory subject(UUID userId) {
    this.subject = userId.toString();
    return this;
  }

  /** Fija el sujeto del token con el valor literal indicado. */
  public TestJwtFactory subject(String subject) {
    this.subject = subject;
    return this;
  }

  /** Fija el emisor {@code iss} del token. */
  public TestJwtFactory issuer(String issuer) {
    this.issuer = issuer;
    return this;
  }

  /** Fija la fecha de emisión del token. */
  public TestJwtFactory issuedAt(Instant issuedAt) {
    this.issuedAt = issuedAt;
    return this;
  }

  /** Fija la fecha de expiración del token. */
  public TestJwtFactory expiresAt(Instant expiresAt) {
    this.expiresAt = expiresAt;
    return this;
  }

  /** Genera un token ya expirado. */
  public TestJwtFactory expired() {
    this.issuedAt = Instant.now().minusSeconds(600);
    this.expiresAt = Instant.now().minusSeconds(60);
    return this;
  }

  /** Añade una audiencia {@code aud} al token. */
  public TestJwtFactory audience(String audience) {
    this.audiences.add(audience);
    return this;
  }

  /** Añade el claim {@code nonce} al token. */
  public TestJwtFactory nonce(String nonce) {
    this.nonce = nonce;
    return this;
  }

  /** Añade un claim adicional con el nombre y valor indicados. */
  public TestJwtFactory claim(String name, Object value) {
    this.claims.put(name, value);
    return this;
  }

  /** Cambia el nombre del claim que transporta los roles. */
  public TestJwtFactory rolesClaim(String rolesClaim) {
    this.rolesClaim = rolesClaim;
    return this;
  }

  /** Fija los roles que transporta el token. */
  public TestJwtFactory roles(String... roles) {
    this.roles = List.of(roles);
    return this;
  }

  /** Firma el token con el par de claves indicado. */
  public TestJwtFactory signedWith(TestRsaKey key) {
    this.key = key;
    return this;
  }

  /**
   * Serializa y firma el token con los valores acumulados en la fábrica.
   *
   * @return token JWT compacto firmado con RS256
   */
  public String build() {
    JWTClaimsSet.Builder claimsBuilder =
        new JWTClaimsSet.Builder()
            .subject(subject)
            .issuer(issuer)
            .issueTime(Date.from(issuedAt))
            .expirationTime(Date.from(expiresAt))
            .claim(rolesClaim, roles);
    if (!audiences.isEmpty()) {
      claimsBuilder.audience(audiences);
    }
    if (nonce != null) {
      claimsBuilder.claim("nonce", nonce);
    }
    claims.forEach(claimsBuilder::claim);

    SignedJWT jwt =
        new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.kid()).build(),
            claimsBuilder.build());
    try {
      jwt.sign(new RSASSASigner(key.privateKey()));
    } catch (Exception exception) {
      throw new IllegalStateException("Cannot sign test token", exception);
    }
    return jwt.serialize();
  }
}
