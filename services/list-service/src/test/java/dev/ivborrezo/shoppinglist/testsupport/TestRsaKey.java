package dev.ivborrezo.shoppinglist.testsupport;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Par de claves RSA de test cargado desde {@code test-keys/} o generado en memoria.
 *
 * <p>El par principal es estable y compartido entre los servicios, de modo que el JWKS servido por
 * {@link TestJwksServer} y los tokens firmados por {@link TestJwtFactory} son reproducibles. La
 * clave alternativa permite firmar tokens con una clave distinta a la publicada para verificar que
 * el servicio rechaza firmas inválidas.
 */
public final class TestRsaKey {

  private static final String PRIVATE_KEY_RESOURCE = "test-keys/test-rsa-private.pem";

  private static final String PUBLIC_KEY_RESOURCE = "test-keys/test-rsa-public.pem";

  private static final String PRIMARY_KID = "test-key-1";

  private static final String ALTERNATIVE_KID = "test-key-2";

  private final String kid;

  private final RSAPrivateKey privateKey;

  private final RSAPublicKey publicKey;

  private TestRsaKey(String kid, RSAPrivateKey privateKey, RSAPublicKey publicKey) {
    this.kid = kid;
    this.privateKey = privateKey;
    this.publicKey = publicKey;
  }

  /**
   * Carga el par RSA principal incluido en {@code src/test/resources/test-keys/}.
   *
   * @return par de claves con el identificador {@code test-key-1}
   */
  public static TestRsaKey primary() {
    return new TestRsaKey(
        PRIMARY_KID, readPrivateKey(PRIVATE_KEY_RESOURCE), readPublicKey(PUBLIC_KEY_RESOURCE));
  }

  /**
   * Genera en memoria un par RSA alternativo para firmar tokens con una clave no publicada.
   *
   * @return par de claves con el identificador {@code test-key-2}
   */
  public static TestRsaKey alternative() {
    try {
      KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(2048);
      var keyPair = generator.generateKeyPair();
      return new TestRsaKey(
          ALTERNATIVE_KID,
          (RSAPrivateKey) keyPair.getPrivate(),
          (RSAPublicKey) keyPair.getPublic());
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("RSA is not available", exception);
    }
  }

  /**
   * Devuelve el identificador {@code kid} con el que se publica la clave en el JWKS.
   *
   * @return identificador de la clave
   */
  public String kid() {
    return kid;
  }

  /**
   * Devuelve la clave privada RSA para firmar tokens.
   *
   * @return clave privada RSA
   */
  public RSAPrivateKey privateKey() {
    return privateKey;
  }

  /**
   * Devuelve la clave pública RSA para publicar en el JWKS.
   *
   * @return clave pública RSA
   */
  public RSAPublicKey publicKey() {
    return publicKey;
  }

  /**
   * Exporta la clave pública como documento JWK Set JSON.
   *
   * @return JWKS con una única clave pública RSA
   */
  public String toJwks() {
    RSAKey jwk = new RSAKey.Builder(publicKey).keyID(kid).build();
    return new JWKSet(jwk).toString();
  }

  private static RSAPrivateKey readPrivateKey(String resource) {
    try {
      byte[] encoded = readPem(resource);
      return (RSAPrivateKey)
          KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(encoded));
    } catch (NoSuchAlgorithmException | InvalidKeySpecException exception) {
      throw new IllegalStateException("Cannot read RSA private key " + resource, exception);
    }
  }

  private static RSAPublicKey readPublicKey(String resource) {
    try {
      byte[] encoded = readPem(resource);
      return (RSAPublicKey)
          KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(encoded));
    } catch (NoSuchAlgorithmException | InvalidKeySpecException exception) {
      throw new IllegalStateException("Cannot read RSA public key " + resource, exception);
    }
  }

  private static byte[] readPem(String resource) {
    try (InputStream input = TestRsaKey.class.getClassLoader().getResourceAsStream(resource)) {
      if (input == null) {
        throw new IllegalStateException("Missing test key resource: " + resource);
      }
      String pem =
          new String(input.readAllBytes(), StandardCharsets.US_ASCII)
              .replaceAll("-----BEGIN [A-Z ]+-----", "")
              .replaceAll("-----END [A-Z ]+-----", "")
              .replaceAll("\\s", "");
      return Base64.getDecoder().decode(pem);
    } catch (IOException exception) {
      throw new UncheckedIOException("Cannot read test key resource " + resource, exception);
    }
  }
}
