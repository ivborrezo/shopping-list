# ADR-004: Estándares de Desarrollo y Gobernanza

## Estado
Redactado

## Contexto

A medida que el proyecto avanza desde la infraestructura base (Fase 1)
hacia la implementación de lógica de negocio, surge la necesidad de
fijar un conjunto de convenciones transversales que no son decisiones
de **arquitectura del sistema** (no afectan a cómo se comunican los
microservicios entre sí, ni a su topología, ni a sus patrones de datos),
sino decisiones de **proceso e higiene de ingeniería**: cómo se
documenta visualmente la arquitectura, cómo se redactan los mensajes de
commit, y qué herramientas de estilo y formato de código se exigen en
cada lenguaje del monorepo.

Cada una de estas tres decisiones, evaluada de forma aislada, no supera
el criterio que este mismo proyecto ya aplica para decidir si algo
merece un ADR propio: una decisión se documenta como ADR cuando es
**irreversible o cara de revertir**. Ni la notación de los diagramas de
arquitectura, ni el formato de los mensajes de commit, ni la
herramienta de linting de estilo son difíciles de cambiar en el futuro
si dejan de encajar. Redactar tres ADRs independientes para estas
decisiones generaría ruido en `docs/adr/` y diluiría el peso de los
ADRs que sí son arquitectónicos (ADR-001, ADR-002, ADR-003).

Sin embargo, las tres comparten una misma motivación raíz —
**estandarización para legibilidad y profesionalización del
repositorio**, tanto para su funcionamiento como producto real como
para su uso como portafolio técnico demostrable— y sí merece quedar
documentada la razón por la que se adoptan de forma conjunta y
consciente, en lugar de derivarse de forma implícita o ad-hoc.

Este ADR se redacta en este momento, no antes ni después: las tres
decisiones ya están tomadas y en aplicación (el primer diagrama C4 ya
existe, el historial de commits ya sigue Conventional Commits desde el
primer commit del repositorio), por lo que documentarlas ahora conserva
el contexto real de la decisión, en lugar de especular sobre algo no
implementado o reconstruir el razonamiento después de haberlo olvidado.

---

## Decisión 1 — Documentación de arquitectura: Markdown + Mermaid/draw.io según el caso

### Contexto específico
El proyecto necesita representar visualmente su arquitectura (Modelo
C4) y, en el futuro, otros artefactos como diagramas de secuencia o
esquemas entidad-relación. La opción inicialmente evaluada para todos
los casos fue **Mermaid.js embebido en Markdown**, por su naturaleza de
texto plano versionable y diffable en Git, sin dependencias de
herramientas externas.

### Decisión
- La documentación de arquitectura del proyecto se redacta en
  **Markdown**, como ya establece la estructura de `/docs` y
  `/<servicio>/docs`.
- **Mermaid no se utiliza en ningún diagrama del proyecto, por el
  momento.** En particular, el diagrama C4 Nivel 2 (Contenedores) se
  evaluó inicialmente en Mermaid y se descartó tras varias iteraciones
  por **calidad visual insuficiente** para un documento con propósito
  de portafolio técnico: limitaciones de control de layout (el motor
  `dagre` no garantiza alineación de nodos en rejilla), ausencia de
  soporte de etiquetas de texto en el tipo de diagrama `block-beta`
  (la única variante de Mermaid que sí permite posicionamiento fijo en
  rejilla), y un resultado estético por debajo del nivel esperado para
  este propósito.
- El diagrama C4 Nivel 2 actual se genera con **draw.io**, exportado
  como imagen estática (`.svg`) para visualización embebida en el
  `.md`, conservando el archivo fuente editable (`.drawio`) junto a
  ella en el repositorio.
- Esta decisión **no es un descarte permanente de Mermaid**: se deja la
  puerta abierta a reincorporarlo en el futuro si aparece un caso de
  uso donde su naturaleza de texto-versionable compense la pérdida de
  calidad visual frente a una herramienta visual como draw.io (por
  ejemplo, diagramas internos de menor exigencia estética, o si las
  capacidades de Mermaid mejoran).
- Como consecuencia de lo anterior, los diagramas de secuencia E2E
  (`docs/flows/`) y los esquemas ER de cada servicio
  (`<servicio>/docs/database-schema.md`), originalmente previstos en
  Mermaid, quedan con **herramienta pendiente de decisión** y se
  evaluarán caso por caso cuando se redacten, sin asumir Mermaid por
  defecto.

### Consecuencias
- **Positivas:** el diagrama C4 alcanza un nivel de pulido visual
  adecuado para su uso en entrevistas técnicas y como pieza de
  portafolio; mayor control sobre tipografía, espaciado y alineación.
- **Negativas (trade-off asumido):** los cambios sobre el `.svg` no son
  legibles como diff de Git — un PR que actualice el diagrama muestra
  un archivo binario/XML reemplazado, no un cambio de texto
  inspeccionable línea a línea. Se mitiga parcialmente conservando el
  `.drawio` fuente junto al `.svg` exportado, y complementando el
  diagrama con tablas en Markdown (p. ej. la "Matriz de Comunicaciones
  e Interacciones" del C4 Nivel 2) que sí permanecen como texto
  diffable para el contenido relacional del diagrama.
- Cada nuevo diagrama del proyecto deberá evaluar individualmente si
  Mermaid es suficiente para su propósito o si requiere una herramienta
  visual, en lugar de asumir una single source of truth de herramienta
  para todos los diagramas del repositorio.

---

## Decisión 2 — Mensajes de commit: Conventional Commits

### Contexto específico
El monorepo aloja múltiples microservicios con pipelines de CI/CD
independientes activados por path filters, y el proyecto contempla
automatizar el versionado en el futuro (cuando existan releases reales
de cada servicio). Un historial de commits sin formato estándar
dificulta tanto la generación automática de changelogs como la lectura
del historial como documentación viva de la evolución del sistema.

### Decisión
Todos los mensajes de commit del repositorio siguen estrictamente la
especificación de **[Conventional Commits](https://www.conventionalcommits.org/)**:

```
<tipo>(<scope opcional>): <descripción breve en imperativo>

<cuerpo opcional explicando el contexto y el porqué>
```

Tipos principales en uso: `feat`, `fix`, `docs`, `chore`, `refactor`,
`test`, `ci`. El scope identifica el área afectada (p. ej. `adr`,
`architecture`, `product-service`, `list-service`).

### Consecuencias
- **Positivas:** habilita la futura automatización de versionado
  semántico (`semantic-release` o equivalente) sin reescribir el
  historial existente; el historial de commits actúa como changelog
  legible incluso antes de automatizarlo.
- **Negativas:** disciplina adicional en cada commit; en un monorepo,
  el scope debe usarse con cuidado para no perder la trazabilidad de a
  qué microservicio afecta cada cambio.

---

## Decisión 3 — Estilo y formato de código: Java y JavaScript

### Contexto específico
El proyecto combina dos lenguajes con responsabilidades distintas:
Java 21 / Spring Boot 3.x en los microservicios backend, y JavaScript
(React) en el frontend (Fase 2). Sin herramientas de verificación y
formato automático, la consistencia del código depende exclusivamente
de la disciplina manual de cada desarrollador, lo que es especialmente
frágil en un proyecto cuyo propósito incluye ser revisado por terceros
(entrevistadores, colaboradores potenciales).

Se distingue deliberadamente entre dos roles complementarios, en lugar
de resolverlos con una única herramienta:
- **Linter:** detecta y reporta violaciones de estilo o patrones
  problemáticos, sin modificar el código automáticamente.
- **Formatter:** reescribe automáticamente el código para que cumpla un
  formato determinado, eliminando la discusión manual sobre estilo en
  revisiones de código.

### Decisión
Se adoptan las siguientes herramientas, una por rol y por lenguaje,
para todos los módulos del monorepo (actuales y los que se añadan en
fases posteriores):

| Lenguaje | Rol | Herramienta | Ruleset |
|---|---|---|---|
| Java | Linter | Checkstyle | Google Java Style |
| Java | Formatter | Spotless | Google Java Format |
| JavaScript / React | Linter | ESLint | — |
| JavaScript / React | Formatter | Prettier | — |

> **Alcance de esta decisión:** este ADR fija **qué herramientas se
> usan**, no **cómo ni cuándo se integran en el pipeline de CI/CD**
> (orden de ejecución, alcance por fase del roadmap, o si actúan de
> forma bloqueante o no sobre el build). Ese diseño se documentará en
> `docs/cicd/cicd-strategy.md` y podrá evolucionar de forma
> independiente sin necesidad de modificar este ADR, ya que el coste de
> ajustar el grado de exigencia de un gate de CI es bajo comparado con
> el de cambiar la herramienta de linting/formato una vez aplicada a
> todo el código existente.

### Consecuencias
- **Positivas:** consistencia de estilo y formato verificable y
  corregible de forma automática, sin depender de revisión manual;
  Google Java Style, Google Java Format, ESLint y Prettier son
  estándares ampliamente reconocidos en la industria, lo que facilita
  que el código sea legible por terceros sin curva de aprendizaje
  adicional.
- **Negativas:** cuatro herramientas distintas que mantener
  actualizadas y configuradas de forma coherente entre servicios;
  posible fricción inicial si el código ya existente no cumple las
  reglas desde el primer momento. El tratamiento de esa fricción
  (bloqueo en CI, alcance retroactivo o no sobre código histórico,
  fases de aplicación) se decide en `docs/cicd/cicd-strategy.md`, no
  aquí.
- Cambiar de herramienta en el futuro (p. ej. sustituir Spotless por
  otro formatter) sigue siendo una decisión de bajo coste mientras el
  código no dependa de configuración específica de la herramienta
  elegida; de ahí que esta decisión, evaluada en aislamiento, no
  exigiría un ADR propio por sí misma. Se documenta aquí por
  formar parte del conjunto de estándares transversales de gobernanza
  de este ADR.

---

## Relación con otros documentos

- `docs/cicd/cicd-strategy.md` — Diseño de integración en el pipeline
  de CI/CD de los linters y formatters fijados en la Decisión 3
  (orden de ejecución, alcance por fase, carácter bloqueante o no).
  Pendiente de redactar.
