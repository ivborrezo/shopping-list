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
- **Referencias a fases: descriptivas, nunca numeradas.** La numeración de
  las fases del proyecto vive únicamente en [`ROADMAP.md`](../../ROADMAP.md),
  su fuente de verdad. Fuera de él, los documentos se refieren a las fases
  por su contenido ("la fase de plataforma", "cuando exista autenticación
  real", "la fase de observabilidad"), no por su número, para no acoplarse a
  una numeración que puede reordenarse.

  La misma regla aplica a los **códigos de ADR pendientes** que codifican una
  fase (`ADR-XXF<n>`): al renumerar el roadmap se renumeran también sus
  referencias, pero la prosa que los acompaña describe la decisión, no la
  fase.
