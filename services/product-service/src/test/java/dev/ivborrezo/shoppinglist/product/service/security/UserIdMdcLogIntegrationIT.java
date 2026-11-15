package dev.ivborrezo.shoppinglist.product.service.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.ivborrezo.shoppinglist.testsupport.TestJwksServer;
import dev.ivborrezo.shoppinglist.testsupport.TestJwtFactory;
import dev.ivborrezo.shoppinglist.testsupport.TestUsers;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.TestConstructor.AutowireMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Test de integración del enriquecimiento del MDC con el usuario autenticado.
 *
 * <p>Comprueba que una petición autenticada publica {@code userId} y {@code correlationId} en el
 * MDC, que ambos se limpian al finalizar la petición y que los logs no exponen el token ni la
 * cabecera de autorización.
 */
@SpringBootTest(
    webEnvironment = WebEnvironment.MOCK,
    properties = {
      "logging.pattern.console=%d %-5level [%X{correlationId}] [%X{userId}] %logger{36} - %msg%n"
    })
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
@TestConstructor(autowireMode = AutowireMode.ALL)
@Testcontainers
class UserIdMdcLogIntegrationIT {

  @Container @ServiceConnection
  static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

  private static final TestJwksServer JWKS = TestJwksServer.start();

  private static final String CORRELATION_ID = "4f4f4f4f-4f4f-4f4f-8f4f-4f4f4f4f4f4f";

  private final MockMvc mockMvc;

  UserIdMdcLogIntegrationIT(MockMvc mockMvc) {
    this.mockMvc = mockMvc;
  }

  @DynamicPropertySource
  static void jwtProperties(DynamicPropertyRegistry registry) {
    registry.add(
        "spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> TestUsers.TEST_ISSUER);
    registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", JWKS::jwksUri);
  }

  @AfterAll
  static void stopJwks() {
    JWKS.stop();
  }

  /**
   * Publica el usuario y la correlación en el MDC de la petición y los limpia al finalizar.
   *
   * @param output salida de consola capturada durante el test
   * @throws Exception si la petición MockMvc falla
   */
  @Test
  void authenticatedRequest_populatesAndClearsMdcWithoutLeakingToken(CapturedOutput output)
      throws Exception {
    String token = TestJwtFactory.token().subject(TestUsers.USER_1).build();

    MvcResult result =
        mockMvc
            .perform(
                get("/mdc-probe")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .header("X-Correlation-Id", CORRELATION_ID))
            .andExpect(status().isOk())
            .andReturn();

    assertThat(result.getResponse().getContentAsString())
        .isEqualTo(TestUsers.USER_1 + "|" + CORRELATION_ID);
    assertThat(MDC.get("userId")).isNull();
    assertThat(MDC.get("correlationId")).isNull();

    List<String> probeLines =
        output.getOut().lines().filter(line -> line.contains("mdc probe")).toList();
    assertThat(probeLines)
        .singleElement()
        .satisfies(
            line ->
                assertThat(line).contains(CORRELATION_ID).contains(TestUsers.USER_1.toString()));
    assertThat(output.getOut()).doesNotContain(token, "Bearer ", "Authorization");
  }

  /** Controller de prueba que expone los valores del MDC y genera una línea de log. */
  @TestConfiguration
  static class MdcProbeConfig {

    /**
     * Endpoint de prueba que devuelve {@code userId|correlationId} y loguea dentro de la petición.
     */
    @RestController
    static class MdcProbeController {

      private static final Logger log = LoggerFactory.getLogger(MdcProbeController.class);

      @GetMapping("/mdc-probe")
      String probe() {
        log.info("mdc probe");
        return MDC.get("userId") + "|" + MDC.get("correlationId");
      }
    }
  }
}
