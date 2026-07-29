# ADR-007: Flyway como herramienta de migraciones

## Estado
Aceptado

## Contexto

ShoppingList adopta el patrón Database-per-Service (ADR-002): cada
microservicio Spring del sistema (`product-service` y, en el futuro,
`list-service`) posee su propia base de datos PostgreSQL, físicamente
separada, gestionada como un detalle de implementación privado del
servicio. ADR-002 fija el *qué* (una base de datos por servicio, sin
acceso compartido entre servicios) pero no el *cómo* se gestiona la
evolución del esquema de cada una de esas bases de datos a lo largo
del tiempo.

Esa gestión del esquema no es un detalle trivial: en un sistema que
pretende ser desplegable como producto real y demostrable como
portafolio técnico, el esquema debe poder reproducirse de forma
idéntica en cualquier entorno (local, CI, producción), su evolución
debe ser auditable en el historial de versiones, y el propio esquema
debe ser la única fuente de verdad sobre su estado — no la inferencia
que Hibernate hace desde las entidades JPA.

La opción por defecto en muchos proyectos Spring que arrancan desde
Spring Initializr es delegar la gestión del esquema en Hibernate
(`spring.jpa.hibernate.ddl-auto=update` o `=create-drop`). Esto es
tentador porque no exige ninguna herramienta adicional: el esquema
se "genera" a partir de las anotaciones `@Entity`. Sin embargo, esa
delegación introduce problemas reales: el esquema generado no es
determinista entre versiones de Hibernate, no captura migraciones
de datos (solo de estructura), no deja rastro auditable de qué
cambios se aplicaron y cuándo, y mezcla la responsabilidad de
*modelado de dominio* con la de *evolución del esquema* — dos
preocupaciones distintas con ciclos de vida distintos.

La decisión sobre la herramienta de migraciones debía tomarse en
el momento del scaffolding de `product-service` (Fase 1), antes de
escribir la primera entidad JPA, porque condiciona directamente cómo
se estructura la carpeta de recursos del servicio, cómo se inicia la
base de datos por primera vez y qué ciclo de vida siguen los cambios
de esquema a partir de ahí. Esta decisión se tomó de forma
irreversible durante la sesión de scaffolding de `product-service`,
y este documento formaliza por escrito una decisión que ya gobernó
esa sesión.

### Versiones del stack como evidencia de un trade-off

La implementación de Flyway en `product-service` se realiza sin fijar
`<version>` explícito en el `pom.xml`: tanto el starter
`spring-boot-starter-flyway` como el módulo de base de datos
`flyway-database-postgresql` heredan su versión del BOM
`spring-boot-dependencies:4.1.0`, que en este momento resuelve Flyway
a la versión 12.4.0.

Delegar el versionado de Flyway en el BOM de Spring Boot es, en sí
mismo, una decisión de diseño con un trade-off real, no un detalle
de relleno: a cambio de renunciar a un control puntual sobre la
versión exacta de Flyway, se obtiene la garantía de que la versión
utilizada ha sido probada en combinación con la versión exacta de
Spring Boot, de JPA y del driver PostgreSQL que también gestiona el
BOM. Las versiones concretas (Spring Boot 4.1.0, Flyway 12.4.0) se
incluyen aquí como evidencia del momento en que se tomó la decisión,
no como un dato a mantener actualizado: el contrato real de
versiones vive en el `pom.xml` y en el BOM.

## Decisión

Se adopta **Flyway** (edición Community) como herramienta de
migraciones versionadas para todos los microservicios Spring del
monorepo: `product-service` en el estado actual y `list-service`
cuando llegue su rama de scaffold. La edición Community, distribuida
libremente, cubre el caso de uso del proyecto (migraciones
versionadas en SQL) sin necesidad de funcionalidades de pago
(migraciones repeatable con checksum, undo de migraciones aplicadas,
etc.).

Las migraciones se ubican, por servicio, en
`src/main/resources/db/migration` y siguen la convención de Flyway
`V<n>__descripcion.sql` (donde `<n>` es un número entero secuencial
que ordena la aplicación de las migraciones).

Como consecuencia directa de esta decisión, la configuración de JPA
fija `spring.jpa.hibernate.ddl-auto: none`: Hibernate no crea ni
valida tablas. **Flyway es el único gestor del esquema** en todos
los entornos (local, CI, producción), garantizando que el esquema
que arranca cualquier servicio es exactamente el que definen los
scripts versionados en su repositorio.

### Alcance

Esta decisión aplica a `product-service` y al futuro `list-service`
(ambos microservicios Spring con base de datos PostgreSQL propia).

**No aplica a notification-service:** según el diseño actual (Fase 5),
es un consumidor de eventos sin base de datos propia — resuelve
estado (idioma, colaboradores) consultando a Keycloak y a
`list-service` en el momento del consumo. Si en el futuro
`notification-service` requiriera persistencia propia, la elección de
herramienta de migraciones para ese contexto (Node.js) sería una
decisión nueva, fuera del alcance de este ADR.

## Consecuencias

### Positivas

- **Repetibilidad del esquema entre entornos**: cualquier despliegue
  del servicio, en cualquier entorno, parte del mismo conjunto de
  migraciones versionadas. No hay esquema "manual" ni dependiente
  de acciones del desarrollador.
- **Evolución del esquema auditable en Git**: cada cambio de esquema
  es un nuevo fichero SQL en el repositorio, revisable en código
  y trazable en el historial de versiones.
- **Separación de responsabilidades**: el modelado de dominio
  (entidades JPA) queda desacoplado de la gestión del esquema.
  Hibernate mapea entidades a tablas existentes; Flyway define cómo
  llegan a existir esas tablas y cómo evolucionan.
- **Integración nativa con el lifecycle de Spring Boot**: vía el
  starter `spring-boot-starter-flyway` y la autoconfiguración modular
  de Spring Boot, las migraciones se aplican automáticamente al
  arrancar el servicio, sin pasos manuales.
- **Idoneidad para el stack**: las migraciones se redactan en SQL
  plano, independiente del lenguaje de programación del servicio,
  lo que facilita su lectura y revisión.

### Negativas / Trade-offs aceptados

- **No soporta undo de migraciones aplicadas** en la edición
  Community: revertir un cambio de esquema equivale a escribir una
  nueva migración que lo deshaga, no a "rollback automático". Se
  asume como coste aceptable: en la práctica, el undo automático es
  una funcionalidad frágil y rara vez utilizable con seguridad en
  producción.
- **Delegar el versionado en el BOM** de Spring Boot (trade-off
  documentado en Contexto): se gana estabilidad de compatibilidad
  probada a cambio de perder control puntual sobre la versión exacta
  de Flyway. Si se detectara un bug en una versión concreta de
  Flyway resuelta por el BOM, habría que sobrescribirla
  explícitamente en el `pom.xml`, asumiendo el riesgo de
  incompatibilidad.
- **Disciplina de un fichero por migración**: la convención de
  Flyway exige rigurosidad en el nombrado y la inmutabilidad de
  migraciones ya aplicadas; una edición de una migración aplicada
  rompe el checksum. Coste operativo menor, pero real.

### Documentación relacionada

- **Cita a ADR-002** (Database-per-Service): ADR-002 fijó el *qué*
  (una base de datos por servicio, sin acceso compartido); este ADR
  materializa el *cómo* se gestiona la evolución del esquema de cada
  una de esas bases de datos. ADR-002 referencia este ADR como
  *Addressed by*.
- `docs/cicd/cicd-strategy.md` (pendiente de redactar) definirá la
  integración de la gate de calidad en CI, incluyendo la verificación
  de que las migraciones Flyway aplican limpiamente en cada push.

## Alternativas consideradas

### Liquibase (descartada)

Herramienta de migraciones de esquema basada en changeSets redactados
en XML, YAML o JSON, con soporte de rollback declarativo y
abstracción sobre el SQL nativo del motor.

**Por qué se descartó**: su mayor expresividad para refactors
complejos (rollback declarativo, precondiciones, cambios
independientes del motor) no se justifica en el alcance actual del
proyecto. Las migraciones se redactan para PostgreSQL, motor único
del backend Spring, y el SQL versionado de Flyway cubre el caso de
uso con menor curva de aprendizaje y menos verbosidad. La
abstracción sobre el motor, ventaja principal de Liquibase en
proyectos multi-motor, no aporta valor cuando no hay varios motores
que soportar.
