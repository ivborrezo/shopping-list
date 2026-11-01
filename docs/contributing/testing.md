# Política de testing

- **TDD** como política por defecto: los tests se escriben antes del código
  de implementación, para toda funcionalidad nueva con lógica de negocio.
- Toda tarea de implementación incluye sus tests; una tarea sin tests
  requiere petición explícita (ej. un spike).

## Mínimo exigido

Cada endpoint o método público relevante tiene al menos un test de caso
feliz y un test de caso de error.

## Naming

- Tests unitarios: sufijo `*Test`.
- Tests de integración: sufijo `*IT`.

La convención es independiente del lenguaje. En **Java**, el sufijo además
determina la fase de ejecución: `*Test` corre con Surefire en la fase
`test`, y `*IT` con Failsafe en la fase `integration-test`. Cada lenguaje
definirá su propio mecanismo de ejecución cuando se incorpore.

## Stack por lenguaje

### Java

- **JUnit 5** como motor de tests.
- **Mockito** para tests unitarios con dependencias mockeadas.
- **AssertJ** para aserciones fluidas.
- **Testcontainers** (PostgreSQL real vía Docker) para tests de
  integración — nunca H2 ni bases de datos en memoria.
- **WireMock + WebTestClient** para tests de integración de servicios sin
  persistencia propia: simulan los destinos HTTP en lugar de levantar una
  base de datos. La regla de «nunca H2 ni bases de datos en memoria» sigue
  vigente para los servicios con persistencia.
- **Spring Boot Test + MockMvc** para tests de controllers, verificados
  contra el `api-contract.yaml` correspondiente (Design-First).

Referencia: [ADR-010](../adr/ADR-010-politica-de-testing-tdd-vs-test-after.md).
