# Convenciones de documentación

- **Datos volátiles y derivados fuera de la documentación.** Las versiones
  (framework, JDK, motor de base de datos, librerías, imágenes de contenedor)
  y los contadores derivados (número de ADRs, de tablas, de códigos, de seeds,
  de tests...) **no se fijan en la prosa ni en los diagramas**: se
  desincronizan solos. Su fuente de verdad es el build (`pom.xml`, BOM,
  `docker-compose.yml`), el código o el repositorio; si se necesita el dato, se
  **referencia**, no se duplica.
- **Excepción.** Cuando el dato **es el objeto de una decisión**, se cita
  explícitamente: por ejemplo, un defecto de una versión concreta de una
  librería, o una versión descartada por un motivo concreto.
