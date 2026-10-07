package dev.ivborrezo.shoppinglist.list.service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Clase de arranque del microservicio {@code list-service}.
 *
 * <p>Punto de entrada que arranca el contexto de Spring Boot y expone el servicio en el puerto
 * configurado ({@code 8082} en el perfil {@code local}).
 */
@SpringBootApplication
public class ListServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(ListServiceApplication.class, args);
  }
}
