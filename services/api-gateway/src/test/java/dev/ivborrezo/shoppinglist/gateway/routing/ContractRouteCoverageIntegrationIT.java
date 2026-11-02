package dev.ivborrezo.shoppinglist.gateway.routing;

import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.request;
import static com.github.tomakehurst.wiremock.client.WireMock.requestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import dev.ivborrezo.shoppinglist.gateway.support.GatewayTestDestinations;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.TestConstructor.AutowireMode;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.yaml.snakeyaml.Yaml;

/**
 * Test de integración de la cobertura del contrato y de sus metadatos públicos.
 *
 * <p>Lee los dos {@code api-contract.yaml} del repositorio, recorre las 13 plantillas y las 24
 * operaciones, y comprueba que cada una llega al servicio dueño. Además valida los metadatos de
 * AD-12: base URL pública, cabecera de correlación, respuestas 429/503 y catálogo de códigos.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@TestConstructor(autowireMode = AutowireMode.ALL)
class ContractRouteCoverageIntegrationIT {

  private static final GatewayTestDestinations DESTINATIONS = GatewayTestDestinations.start();

  private static final String CORRELATION_HEADER_REF = "#/components/headers/XCorrelationId";

  private static final UUID ID = UUID.fromString("1b4e28ba-2fa1-11d2-883f-0016d3cca427");

  private static final UUID ITEM_ID = UUID.fromString("3f2504e0-4f89-41d3-9a0c-0305e82c3301");

  private static final Path CONTRACTS_ROOT = Path.of("..");

  private static final List<String> METHODS = List.of("get", "post", "patch", "delete");

  private final WebTestClient webTestClient;

  /**
   * Inyecta el cliente HTTP por constructor.
   *
   * @param webTestClient cliente contra el servidor real en puerto aleatorio
   */
  @Autowired
  ContractRouteCoverageIntegrationIT(WebTestClient webTestClient) {
    this.webTestClient = webTestClient;
  }

  @DynamicPropertySource
  static void destinations(DynamicPropertyRegistry registry) {
    DESTINATIONS.register(registry);
  }

  @AfterAll
  static void stopDestinations() {
    DESTINATIONS.stop();
  }

  @BeforeEach
  void resetStubs() {
    DESTINATIONS.productService().resetAll();
    DESTINATIONS.listService().resetAll();
  }

  @Test
  void allContractOperations_areForwardedToTheirService() {
    List<Operation> operations = new ArrayList<>();
    for (ContractSource contract : contracts()) {
      operations.addAll(operationsOf(contract.document()));
    }

    assertThat(operations).as("operaciones de los dos contratos").hasSize(24);
    assertThat(operations.stream().map(operation -> operation.template()).distinct().count())
        .as("plantillas de path")
        .isEqualTo(13);

    int productOperations = 0;
    for (Operation operation : operations) {
      WireMockServer owner = ownerOf(operation.template());
      WireMockServer other =
          owner == DESTINATIONS.productService()
              ? DESTINATIONS.listService()
              : DESTINATIONS.productService();
      owner.resetAll();
      other.resetAll();
      String httpMethod = operation.method().toUpperCase();
      owner.stubFor(request(httpMethod, urlPathEqualTo(operation.path())).willReturn(okJson("{}")));

      int status =
          webTestClient
              .method(HttpMethod.valueOf(httpMethod))
              .uri(operation.path())
              .exchange()
              .returnResult(String.class)
              .getStatus()
              .value();
      assertThat(status)
          .as("%s %s debe llegar al servicio", operation.method(), operation.template())
          .isEqualTo(200);

      owner.verify(1, requestedFor(httpMethod, urlPathEqualTo(operation.path())));
      assertThat(other.getAllServeEvents())
          .as("%s %s no debe llegar a %s", operation.method(), operation.template(), other)
          .isEmpty();

      if (owner == DESTINATIONS.productService()) {
        productOperations++;
      }
    }

    assertThat(productOperations).as("operaciones de product-service").isEqualTo(16);
  }

  @Test
  void contractsDeclareAd12Metadata() throws IOException {
    for (ContractSource contract : contracts()) {
      assertPublicServer(contract.document());
      assertCorrelationParameter(contract.document());
      assertCorrelationHeader(contract.document());
      assertSharedErrorHeaders(contract.document());
      assertOperationCoverage(contract);
      assertErrorCodes(contract.document());
      assertEdgeNotes(contract.document());
    }
  }

  private void assertPublicServer(Map<String, Object> contract) {
    List<Object> servers = list(contract.get("servers"));
    assertThat(servers).as("servers").hasSize(1);
    assertThat(map(servers.getFirst()).get("url"))
        .as("base URL pública")
        .isEqualTo("http://localhost:8080");
  }

  private void assertCorrelationParameter(Map<String, Object> contract) {
    Map<String, Object> parameter =
        map(map(map(contract.get("components")).get("parameters")).get("XCorrelationId"));
    assertThat(parameter.get("name")).isEqualTo("X-Correlation-Id");
    assertThat(parameter.get("in")).isEqualTo("header");
    Map<String, Object> schema = map(parameter.get("schema"));
    assertThat(schema.get("type")).isEqualTo("string");
    assertThat(schema.get("format")).isEqualTo("uuid");

    for (Map.Entry<String, Object> path : map(contract.get("paths")).entrySet()) {
      List<Object> parameters = list(map(path.getValue()).get("parameters"));
      assertThat(parameters)
          .as("cabecera de correlación en el path item %s", path.getKey())
          .anySatisfy(
              entry ->
                  assertThat(map(entry).get("$ref"))
                      .isEqualTo("#/components/parameters/XCorrelationId"));
    }
  }

  private void assertCorrelationHeader(Map<String, Object> contract) {
    Map<String, Object> header =
        map(map(map(contract.get("components")).get("headers")).get("XCorrelationId"));
    assertThat(map(header.get("schema")).get("type")).isEqualTo("string");
    assertThat(map(header.get("schema")).get("format")).isEqualTo("uuid");
  }

  private void assertSharedErrorHeaders(Map<String, Object> contract) {
    Map<String, Object> responses = map(map(contract.get("components")).get("responses"));
    for (String name :
        List.of("NotFound", "ValidationError", "RateLimited", "ServiceUnavailable")) {
      assertHeader(responses.get(name), name);
    }
    if (responses.containsKey("Forbidden")) {
      assertHeader(responses.get("Forbidden"), "Forbidden");
    }
  }

  private void assertOperationCoverage(ContractSource contract) {
    int operations = 0;
    int rateLimited = 0;
    int serviceUnavailable = 0;
    for (Map.Entry<String, Object> path : map(contract.document().get("paths")).entrySet()) {
      for (String method : METHODS) {
        Object operation = map(path.getValue()).get(method);
        if (operation == null) {
          continue;
        }
        operations++;
        Map<String, Object> responses = map(map(operation).get("responses"));
        assertThat(map(responses.get("429")).get("$ref"))
            .as("429 en %s %s", method, path.getKey())
            .isEqualTo("#/components/responses/RateLimited");
        rateLimited++;
        Object unavailable = responses.get("503");
        if (unavailable != null) {
          if (isReference(unavailable)) {
            assertThat(map(unavailable).get("$ref"))
                .as("503 en %s %s", method, path.getKey())
                .isEqualTo("#/components/responses/ServiceUnavailable");
            serviceUnavailable++;
          } else {
            assertHeader(unavailable, method + " " + path.getKey());
          }
        }
      }
    }

    assertThat(operations).as("operaciones").isEqualTo(rateLimited);
    if (contract.kind() == ContractKind.LIST_SERVICE) {
      assertThat(operations).isEqualTo(8);
      assertThat(serviceUnavailable).isEqualTo(7);
    } else {
      assertThat(operations).isEqualTo(16);
      assertThat(serviceUnavailable).isEqualTo(16);
    }
  }

  private void assertErrorCodes(Map<String, Object> contract) {
    Map<String, Object> problemDetail =
        map(map(map(contract.get("components")).get("schemas")).get("ProblemDetail"));
    Map<String, Object> properties = map(problemDetail.get("properties"));
    Map<String, Object> code = map(properties.get("code"));
    List<Object> catalog = list(code.get("enum"));
    assertThat(catalog).contains("RATE_LIMIT_EXCEEDED", "SERVICE_UNAVAILABLE");
    assertThat(catalog).doesNotContain("INTERNAL_ERROR");
  }

  private void assertEdgeNotes(Map<String, Object> contract) {
    String description = (String) map(contract.get("info")).get("description");
    assertThat(description).contains("ROUTE_NOT_FOUND");
    assertThat(description).contains("X-Correlation-Id");
    assertThat(description).contains("no idempotentes");
    assertThat(description).contains("1 MB");
    assertThat(description).contains("Content-Length");
    assertThat(description).contains("CORS");
  }

  private void assertHeader(Object response, String context) {
    Map<String, Object> headers = map(map(response).get("headers"));
    assertThat(headers).as("cabecera de correlación en %s", context).containsKey("X-CorrelationId");
    assertThat(map(headers.get("X-CorrelationId")).get("$ref"))
        .as("referencia de cabecera en %s", context)
        .isEqualTo(CORRELATION_HEADER_REF);
  }

  private List<ContractSource> contracts() {
    return List.of(
        new ContractSource(
            ContractKind.PRODUCT_SERVICE,
            readContract(CONTRACTS_ROOT.resolve("product-service/docs/api-contract.yaml"))),
        new ContractSource(
            ContractKind.LIST_SERVICE,
            readContract(CONTRACTS_ROOT.resolve("list-service/docs/api-contract.yaml"))));
  }

  private Map<String, Object> readContract(Path path) {
    if (!Files.isRegularFile(path)) {
      throw new AssertionError("No se encontró el contrato: " + path.toAbsolutePath());
    }
    try (InputStream input = Files.newInputStream(path)) {
      return map(new Yaml().load(input));
    } catch (IOException exception) {
      throw new AssertionError("No se pudo leer el contrato: " + path.toAbsolutePath(), exception);
    }
  }

  private List<Operation> operationsOf(Map<String, Object> contract) {
    List<Operation> operations = new ArrayList<>();
    for (Map.Entry<String, Object> path : map(contract.get("paths")).entrySet()) {
      for (String method : METHODS) {
        if (map(path.getValue()).containsKey(method)) {
          operations.add(new Operation(method, path.getKey(), concretePath(path.getKey())));
        }
      }
    }
    return operations;
  }

  private String concretePath(String template) {
    return template.replace("{itemId}", ITEM_ID.toString()).replace("{id}", ID.toString());
  }

  private WireMockServer ownerOf(String template) {
    if (template.startsWith("/lists")) {
      return DESTINATIONS.listService();
    }
    if (template.startsWith("/categories")
        || template.startsWith("/base-products")
        || template.startsWith("/user-products")) {
      return DESTINATIONS.productService();
    }
    throw new AssertionError("Prefijo sin ruta declarada en el gateway: " + template);
  }

  private boolean isReference(Object response) {
    return map(response).containsKey("$ref");
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> map(Object value) {
    return (Map<String, Object>) value;
  }

  @SuppressWarnings("unchecked")
  private static List<Object> list(Object value) {
    return (List<Object>) value;
  }

  private enum ContractKind {
    PRODUCT_SERVICE,
    LIST_SERVICE
  }

  private record ContractSource(ContractKind kind, Map<String, Object> document) {}

  private record Operation(String method, String template, String path) {}
}
