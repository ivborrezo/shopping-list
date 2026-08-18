# ADR-010: Politica de Testing (TDD vs. test-after)

## Estado

Redactado

## Contexto

Hasta la Rama 2 de `product-service`, el proyecto no habia escrito
logica de negocio no trivial que justificara decidir una politica de
testing. La Rama 1 fue un scaffold de directorios y configuracion, y
la Rama 2 un walking skeleton (`GET /categories` sin i18n). En ambas
el codigo de implementacion existia antes que los tests (test-after),
y era suficiente para el proposito de esas ramas: validar que el wiring
de Spring, el contenedor PostgreSQL, las migraciones Flyway y el
pipeline de CI funcionaban juntos.

La Rama 3 de `product-service` introduce la primera logica de negocio
no trivial del proyecto: resolucion de la cabecera `Accept-Language`
con fallback a ingles, validaciones en dos capas (controller y
servicio), y el primer endpoint de escritura (`POST /categories`). En
este punto la decision sobre como y cuando escribir tests ya no es
especulativa: hay casos concretos de logica de negocio que se prestan a
ser testeados antes de implementarse (ej. el metodo `resolveName` que
busca una traduccion en un `Set<CategoryTranslation>` con fallback a
ingles) y flujos E2E que requieren tests de integracion (ej.
`POST /categories` con validacion y mensajes de error).

El proyecto ya fija en [ADR-008](ADR-008-testcontainers-para-testing-de-integracion.md)
la herramienta de integracion (Testcontainers con PostgreSQL real) y en
[ADR-009](ADR-009-estrategia-de-ci-y-git-hooks.md) la estrategia de CI
y la convencion de naming de tests (`*Test` / `*IT` /
`*IntegrationTest`). La seccion 4 del AGENTS.md del proyecto ya exige
tests como parte de cada entregable, con minimo de caso feliz y caso de
error por endpoint. Lo que ninguno de esos documentos fija es el
**cuando** (antes o despues del codigo) ni el **como** (que tipo de
test para que caso).

Este ADR cierra ese vacio. Se redacta en este momento, no antes ni
despues: antes de la Rama 3 habria sido especular sobre algo no
implementado; despues de la Rama 3 habria sido una decision tomada de
facto sin documentar. La primera tarea de la Rama 3 que implique
escribir tests (test de `resolveName`, test de integracion de
`POST /categories`) se convierte en el primer ejercicio real de esta
politica.

## Decisiones

### Decision 1 — TDD como politica por defecto

TDD (test-first) es la politica por defecto para toda funcionalidad
nueva con logica de negocio en los servicios Spring del monorepo. Los
tests se escriben **antes** del codigo de implementacion (fase Red), el
codigo se escribe para hacerlos pasar (fase Green), y luego se
refactoriza con la red de seguridad de los tests (fase Refactor).

**Alcance:** servicios Spring con logica de negocio (`product-service`,
futuro `list-service`). No aplica a `notification-service` (Node.js)
en este momento: la politica de testing para ese servicio se decidira
cuando llegue su implementacion (Fase 5).

**Motivacion:** dos razones concretas, ancladas en el contexto real del
proyecto:

- Contrato ejecutable verificable automaticamente: los tests
  escritos antes del codigo actuan como especificacion ejecutable de lo
  que el codigo debe hacer. Sin tests previos, la unica validacion
  posible es la revision manual; con tests previos, la validacion es
  automatica y repetible, y cualquier desviacion del comportamiento
  esperado se detecta en el momento.
- **Portafolio tecnico demostrable:** uno de los propositos explicitos
  del proyecto es servir como evidencia de competencias de ingenieria.
  Un historial de commits que refleje el ciclo Red-Green-Refactor es
  una senal mas fuerte que un historial donde los tests siempre aparecen
  en el mismo commit que el codigo.

**Que NO dice esta decision:**

- No prescribe cobertura minima. Esa metrica se decidira cuando haya
  datos reales de cobertura sobre un volumen representativo de codigo;
  fijar un porcentaje ahora seria arbitrario.
- No cambia lo que ya exige AGENTS.md seccion 4: los tests siguen
  siendo parte del entregable de cada tarea. Este ADR anade el *cuando*
  (antes del codigo), no sustituye el *que* (tests obligatorios).
- No obliga a reescribir tests de codigo existente. Las Ramas 1 y 2 de
  `product-service` fueron test-after y se respetan tal cual. La
  politica TDD aplica hacia adelante, no retroactivamente.

### Decision 2 — Tipos de test y su ambito

Se definen dos tipos de test que coexisten, no compiten:

**Test unitario (Mockito, sin Spring):** para logica de negocio aislada
y testeable sin dependencias externas. El test no levanta contexto de
Spring, no arranca PostgreSQL, no requiere Testcontainers. Ejemplo
concreto: el metodo `resolveName(Category, Locale)` que busca una
traduccion en un `Set<CategoryTranslation>` con fallback a ingles.

**Test de integracion (MockMvc + Testcontainers):** para flujos E2E que
atraviesan todas las capas (controller, servicio, repositorio,
PostgreSQL). El test levanta el contexto de Spring completo, arranca un
contenedor PostgreSQL efimero con Testcontainers, aplica las
migraciones Flyway, y verifica el comportamiento contra la API HTTP.
Ejemplo concreto: `POST /categories` con validacion y mensajes de error,
donde interesa probar que Spring
`AcceptHeaderLocaleResolver`, nuestro `CategoryService`, las
validaciones de Jakarta Bean Validation y las traducciones en BD
funcionan juntos.

**Ambos tipos coexisten:** un metodo con logica de negocio relevante se
testea unitariamente con Mockito y ademas se cubre indirectamente en
los tests de integracion E2E. La piramide de testing se respeta: muchos
unitarios (rapidos, aislados, feedback en milisegundos), pocos de
integracion (lentos, cobertura amplia, feedback en segundos).

**Regla de decision pragmatica:**

- Si el metodo tiene logica de decision (if/else, streams con filter,
  excepciones): test unitario.
- Si el flujo involucra Spring wiring, HTTP, BD, o configuracion: test
  de integracion.
- Si es mapeo trivial entidad-DTO sin logica: no justifica test
  unitario propio; se cubre en integracion.

**Convencion de naming:** los tests unitarios se nombran `*Test`, los
de integracion con Testcontainers `*IT` o `*IntegrationTest`. Esta
convencion fue adoptada como micro-decision en ADR-009 Decision 8 y se
confirma aqui sin reabrir aquel ADR. La convencion prepara la futura
separacion de plugins Surefire/Failsafe sin migracion de nombres.

### Decision 3 — Excepciones legitimas al TDD

TDD es el default, pero no es dogma. Se reconocen estas excepciones:

- **(a) Walking skeleton / scaffold:** cuando la forma final de las
  clases no se conoce de antemano y emerge incrementalmente (ej. la
  Rama 2 de `product-service`). Escribir tests antes habria sido
  especular sobre una estructura de clases que aun no estaba definida.
- **(b) CRUD sin logica de negocio:** mapeo entidad-DTO, endpoints que
  solo delegan en el servicio sin transformacion ni validacion mas alla
  de la que ofrece el framework (Jakarta Bean Validation basica sin
  logica custom). Tests after.
- **(c) Cambios de build / infraestructura:** `pom.xml`, `Dockerfile`,
  `docker-compose.yml`, workflows de CI. Estos artefactos se validan
  por construccion (el build falla o no), no mediante tests unitarios
  escritos antes.

Toda excepcion debe declararse **antes** de empezar la implementacion:

- Si la excepcion afecta a una rama entera: se documenta en el plan de
  la rama.
- Si la excepcion afecta a un paso concreto dentro de una rama que por
  lo demas sigue TDD: se indica en el paso correspondiente.

No hace falta un ADR por cada excepcion; basta con que quede registrada
en el historial del chat y en el commit message del paso.

### Decision 4 — Separacion Surefire/Failsafe (no aplica hoy)

Se confirma la Decision 8 de
[ADR-009](ADR-009-estrategia-de-ci-y-git-hooks.md): la separacion
Surefire/Failsafe esta fuera de alcance mientras no existan tests
unitarios aislables que se vean penalizados por el arranque del
contenedor Testcontainers. La convencion de naming de la Decision 2
(`*Test` / `*IT` / `*IntegrationTest`) prepara el terreno para una
migracion trivial cuando llegue el momento. No se reabre ADR-009.

## Consecuencias

### Positivas

- Contrato ejecutable verificable automaticamente: los tests
  escritos antes del codigo actuan como especificacion ejecutable del
  comportamiento esperado, permitiendo una validacion automatica y
  repetible que complementa la revision manual.
- **Documentacion ejecutable del contrato:** un test unitario de
  `resolveName` que cubre los casos "traduccion existe en euskera",
  "traduccion no existe, fallback a ingles" y "ni euskera ni ingles,
  fallback al primer idioma disponible" documenta el comportamiento
  esperado de forma mas precisa y mantenible que un comentario o un
  Javadoc.
- **Reduccion de rework:** el ciclo Red-Green-Refactor fuerza a definir
  que debe hacer el codigo antes de escribirlo, lo que reduce la
  probabilidad de implementar algo que luego no encaja y hay que
  reescribir.
- **Coherencia con el objetivo de portafolio:** un proyecto que
  demuestra TDD en su historial de commits es una senal de madurez de
  ingenieria mas fuerte que un proyecto con tests post-hoc.

### Negativas / Trade-offs aceptados

- **Mayor tiempo inicial por slice:** escribir el test antes que el
  codigo anade una fase explicita que no existia en test-after. En un
  slice pequeno (ej. un metodo de 20 lineas) la diferencia es marginal;
  en un flujo E2E puede ser significativa. Se asume como coste de
  calidad.
- **Riesgo de sobre-especificacion en tests:** si el test acopla
  demasiado a detalles de implementacion (ej. verificar que se llama a
  un metodo concreto de un mock en vez de verificar el resultado), el
  test se vuelve fragil y penaliza la refactorizacion. La mitigacion
  esta en la disciplina del autor al escribir los tests, no en la
  herramienta.
- **Friccion en logica delegada a frameworks:** hay comportamientos
  cuyo valor real esta en la integracion con el framework (ej. que
  `@Valid` dispare la validacion de Jakarta Bean Validation), no en la
  logica aislada. Testear eso unitariamente con mocks no anade valor
  real. La regla de decision de la Decision 2 (logica de decision ->
  unitario; wiring -> integracion) mitiga este riesgo.

## Alternativas consideradas

### Test-after uniforme (descartada)

Escribir todos los tests despues del codigo de implementacion, como se
hizo en las Ramas 1 y 2.

**Por que se descarto:** mas rapido inicialmente (no hay fase Red
explicita), pero el codigo se escribe sin un contrato ejecutable previo
que defina el comportamiento esperado, y no demuestra TDD en el
historial de commits del portafolio. Para las Ramas 1 y 2
era la opcion correcta (no habia logica que testear); a partir de la
Rama 3, la logica de negocio existe y TDD aporta valor real.

### TDD dogmatico sin excepciones (descartada)

Aplicar TDD a cada linea de codigo del proyecto, sin admitir
excepciones.

**Por que se descarto:** genera friccion innecesaria en walking
skeletons y scaffolds, donde la forma de las clases no se conoce de
antemano y emerge incrementalmente. Escribir tests antes en ese
contexto es especular sobre una estructura que cambiara, lo que genera
rewrite de tests sin valor. Las excepciones documentadas de la Decision
3 reconocen esta realidad sin diluir la politica general.

### Solo tests de integracion, sin unitarios (descartada)

Cubrir toda la logica de negocio exclusivamente mediante tests de
integracion con Testcontainers + MockMvc.

**Por que se descarto:** lentos (arranque de contenedor PostgreSQL por
clase de test), fragiles (un fallo de infraestructura rompe tests de
logica no relacionada), y no aislan la logica de negocio del wiring de
Spring. Incumple la piramide de testing y convierte el feedback de los
tests en un ciclo de segundos en vez de milisegundos.

### Solo tests unitarios, sin integracion (descartada)

Testear toda la logica unitariamente con Mockito y asumir que el wiring
de Spring funciona.

**Por que se descarto:** no prueba que las capas funcionan juntas
(controller, servicio, repositorio, PostgreSQL). Un test unitario de
`CategoryService` con el repositorio mockeado no detecta que una query
JPQL es sintacticamente incorrecta o que una constraint de BD rechaza
un valor que el servicio considera valido. Genera falsa confianza.

### BDD / Cucumber (descartada)

Anadir una capa de especificaciones en lenguaje natural (Gherkin)
ejecutables via Cucumber sobre los tests de integracion.

**Por que se descarto:** anade una capa de abstraccion y tooling
(Cucumber JVM, ficheros `.feature`, glue code) que no se justifica con
un unico desarrollador y sin stakeholder no tecnico que lea los tests.
El valor de BDD aparece cuando hay un Product Owner o QA que escribe o
valida las especificaciones en Gherkin; en un proyecto individual, el
coste de mantener dos representaciones del mismo test (la feature en
Gherkin y el codigo Java que la implementa) supera el beneficio.

## Documentacion relacionada

- **AGENTS.md seccion 4 (Testing):** ya exige tests como parte de cada
  entregable, con un minimo de caso feliz y caso de error por endpoint
  o metodo publico relevante, y fija el stack de testing (JUnit 5,
  Mockito, AssertJ, Testcontainers, MockMvc). Este ADR anade el
  *cuando* (TDD: tests antes del codigo) y el *como* (que tipo de test
  para que caso: Decision 2), sin sustituir lo ya exigido en AGENTS.md.
- **[ADR-008](ADR-008-testcontainers-para-testing-de-integracion.md):**
  fija Testcontainers con modulo PostgreSQL como herramienta de
  integracion y `@ServiceConnection` como patron de wiring. Este ADR
  asume esa decision y especifica en la Decision 2 cuando usar
  Testcontainers (flujos E2E con Spring wiring, HTTP, BD) vs Mockito
  (logica aislada sin dependencias externas).
- **[ADR-009](ADR-009-estrategia-de-ci-y-git-hooks.md):** su Decision 8
  deja la separacion Surefire/Failsafe fuera de alcance con criterio de
  revisita, y adopta la convencion de naming `*Test` / `*IT` /
  `*IntegrationTest`. Este ADR confirma ambas sin reabrirlas
  (Decisiones 2 y 4). La materializacion operativa en
  `docs/cicd/cicd-strategy.md` no se ve afectada por este ADR.
