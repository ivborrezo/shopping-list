# ADR-008: Testcontainers para testing de integración

## Estado
Aceptado

## Contexto

Los microservicios Spring de ShoppingList (`product-service` en el
estado actual, `list-service` en el futuro) exponen APIs que se apoyan
fuertemente en el comportamiento de PostgreSQL: consultas JPQL/SQL
nativas, constraints, tipos de datos específicos del motor,
triggers, índices y migraciones Flyway. Verificar que el código
funciona no se reduce a comprobar la lógica de negocio en memoria:
requiere comprobar que la interacción con PostgreSQL real se
comporta como se espera.

La opción por defecto en muchos proyectos Spring para los tests de
integración es **H2** en modo de compatibilidad PostgreSQL: una base
de datos en memoria, sin necesidad de Docker, que arranca en
milisegundos. Esta opción es tentadora por su velocidad y por su
simplicidad operativa, pero introduce un riesgo estructural: H2 no
es PostgreSQL. Sus diferencias de comportamiento —funciones SQL
distintas, tratamiento de tipos, semántica de constraints,
comportamiento de transacciones, SQL nativo soportado— generan la
falsa confianza de un test que pasa en H2 y falla en producción.
Un test que se ejecuta contra un motor distinto al de producción
valida, en el mejor de los casos, que el código funciona contra ese
otro motor, no contra el motor real.

La decisión sobre la herramienta de testing de integración debía
tomarse en el momento del scaffolding de `product-service` (Fase 1),
antes de escribir el primer test de integración, porque condiciona
la estructura de los tests del servicio, las dependencias de test
del `pom.xml` y la forma en que el contexto de Spring se levanta en
los tests. Esta decisión se tomó de forma irreversible durante la
sesión de scaffolding de `product-service`, y este documento
formaliza por escrito una decisión que ya gobernó esa sesión.

Los tests de integración con Testcontainers no reimplementan las
migraciones Flyway: **levantan el mismo contenedor PostgreSQL
efímero y le aplican las migraciones Flyway reales** del servicio
(ADR-007). Es decir, el ciclo de tests reproduce el ciclo de arranque
de producción: contenedor PostgreSQL limpio → Flyway aplica las
migraciones versionadas → el contexto de Spring Boot se conecta al
contenedor → los tests se ejecutan contra ese esquema real. Esto
cierra el círculo entre las decisiones de migración (ADR-007) y de
testing (este ADR).

### Versiones del stack como evidencia de un trade-off

Mismo trade-off de delegación en el BOM descrito en ADR-007 para
Flyway: los artefactos `org.testcontainers:testcontainers-postgresql`,
`org.testcontainers:testcontainers-junit-jupiter` y
`org.springframework.boot:spring-boot-testcontainers` se declaran sin
`<version>` explícito en el `pom.xml`, heredándola del BOM
`spring-boot-dependencies:4.1.0` (que en este momento resuelve
Testcontainers a 2.0.5). Las versiones concretas se citan aquí como
evidencia del momento de la decisión, no como dato a mantener
actualizado; el contrato real de versiones vive en el `pom.xml` y en
el BOM.

## Decisión

Se adopta **Testcontainers** con el módulo PostgreSQL como herramienta
de testing de integración para todos los microservicios Spring del
monorepo: `product-service` en el estado actual y `list-service`
cuando llegue su rama de scaffold.

Cada test de integración arranca un **contenedor PostgreSQL efímero y
aislado**: la base de datos se crea limpia al inicio del test (o de la
clase de test, según el alcance que defina la implementación) y se
destruye al finalizar, sin estado compartido entre ejecuciones. Las
migraciones Flyway del servicio (ADR-007) se aplican contra ese
contenedor efímero, reproduciendo el ciclo de arranque de producción.

Como patrón de wiring recomendado entre Testcontainers y Spring Boot,
se indica **`@ServiceConnection`** (proporcionado por el módulo
`spring-boot-testcontainers`): permite que el contexto de Spring
resuelva las propiedades de conexión del datasource contra el
contenedor levantado por el test, sin necesidad de mantener
`@DynamicPropertySource` manual. La configuración concreta de este
patrón (clases reusables, alcance por test vs. por clase, container
reuse) queda fuera del alcance de este ADR y se decidirá en la
implementación de los tests de cada servicio.

### Alcance

Esta decisión aplica a `product-service` y al futuro `list-service`
(ambos microservicios Spring con base de datos PostgreSQL propia).

**No aplica a notification-service:** según el diseño actual (Fase 5),
es un consumidor de eventos sin base de datos propia — resuelve
estado (idioma, colaboradores) consultando a Keycloak y a
`list-service` en el momento del consumo. Si en el futuro
`notification-service` requiriera persistencia propia, la elección de
herramienta de testing de integración para ese contexto (Node.js)
sería una decisión nueva, fuera del alcance de este ADR.

## Consecuencias

### Positivas

- **Sin discrepancias H2↔PostgreSQL**: los tests se ejecutan contra
  el mismo motor de producción. Lo que pasa en CI es lo que pasa en
  producción, a nivel de comportamiento del motor de base de datos.
- **Reproducción del ciclo de arranque real**: las migraciones
  Flyway se ejecutan en cada test, verificando no solo la lógica de
  negocio sino también la integridad de las migraciones aplicadas
  sobre un esquema limpio.
- **Tests aislados y repetibles**: cada ejecución parte de un
  estado limpio, sin contaminación entre tests ni dependencia de
  datos previamente cargados a mano.
- **Integración nativa con Spring Boot Test** vía el módulo
  `spring-boot-testcontainers` y el patrón `@ServiceConnection`.

### Negativas / Trade-offs aceptados

- **Requiere Docker disponible en el entorno de ejecución** del test:
  en local es transparente (Docker ya es prerrequisito del proyecto),
  pero en CI implica Docker-in-Docker o equivalentes en el runner de
  GitHub Actions. La estrategia concreta para resolver esto se decide
  en `docs/cicd/cicd-strategy.md` (pendiente de redactar); es uno de
  los puntos a vigilar en la rama 2, donde se configura por primera
  vez el pipeline de CI.
- **Overhead de arranque por test**: levantar un contenedor PostgreSQL
  tiene un coste de segundos, no de milisegundos. Mitigable con
  configuración reutilizable (container reuse, shared containers por
  clase de test), pero esa configuración se decide en la
  implementación, no en este ADR.
- **Delegar el versionado en el BOM** de Spring Boot (trade-off
  documentado en Contexto): se gana estabilidad de compatibilidad
  probada a cambio de perder control puntual sobre la versión exacta
  de Testcontainers. Mismo trade-off que el descrito en ADR-007 para
  Flyway.

### Documentación relacionada

- **Cita a ADR-007** (Flyway como herramienta de migraciones): los
  tests de integración con Testcontainers aplican las migraciones
  Flyway reales del servicio contra el contenedor PostgreSQL efímero.
  La integridad de las migraciones se verifica de forma implícita en
  cada test.
- `docs/cicd/cicd-strategy.md` (pendiente de redactar) definirá la
  estrategia de Docker-in-Docker en GitHub Actions para los tests
  con Testcontainers, así como la gate de calidad en CI.

## Alternativas consideradas

### H2 en modo compatibilidad PostgreSQL (descartada)

Base de datos en memoria, sin dependencia de Docker, con un modo
de compatibilidad SQL que imita parcialmente el dialecto de
PostgreSQL.

**Por qué se descartó**: las diferencias de comportamiento entre H2
y PostgreSQL son estructurales, no puntuales. Funciones SQL
distintas, tratamiento distinto de tipos de datos, semántica
distinta de constraints, soporte dispar de SQL nativo. Un test que
pasa en H2 no garantiza que el código funcione en PostgreSQL; en
el peor caso, da una falsa confianza que oculta un bug que solo
aparecerá en producción. Para un proyecto cuyo propósito explícito
es ser desplegable como producto real y demostrable como portafolio
técnico, esa falsa confianza es un coste inaceptable. Verificar
contra el motor real desde el primer test elimina una clase entera
de bugs que solo aparecen al desplegar.
