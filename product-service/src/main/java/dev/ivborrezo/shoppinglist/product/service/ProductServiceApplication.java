package dev.ivborrezo.shoppinglist.product.service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Clase de arranque del microservicio {@code product-service}.
 *
 * <p>Punto de entrada que arranca el contexto de Spring Boot y expone el servicio en el puerto
 * configurado ({@code 8081} en el perfil {@code local}).
 */
@SpringBootApplication
public class ProductServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(ProductServiceApplication.class, args);
  }
}
