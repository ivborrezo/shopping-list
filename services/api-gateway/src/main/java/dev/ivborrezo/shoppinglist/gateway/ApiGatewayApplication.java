package dev.ivborrezo.shoppinglist.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Aplicación de arranque del API Gateway. */
@SpringBootApplication
public class ApiGatewayApplication {

  /**
   * Arranca el gateway.
   *
   * @param args argumentos de línea de comandos
   */
  public static void main(String[] args) {
    SpringApplication.run(ApiGatewayApplication.class, args);
  }
}
