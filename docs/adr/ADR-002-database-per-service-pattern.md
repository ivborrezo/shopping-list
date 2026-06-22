# ADR-002: Database-per-Service Pattern

## Estado
Aceptado

## Contexto

ShoppingList se diseña desde el inicio como un sistema de microservicios,
con `product-service` y `list-service` como los dos primeros servicios
de negocio (Fase 1 — MVP Core). Antes de implementar la lógica de
negocio de ninguno de los dos, era necesario decidir cómo se relacionan
sus datos a nivel de persistencia.

La alternativa por defecto en muchos proyectos que migran desde un
monolito —o que parten de cero sin experiencia previa en microservicios
en producción— es la **Shared Database**: una única base de datos
relacional compartida por todos los servicios, donde las relaciones
entre entidades de distintos dominios (por ejemplo, un `list_item` que
referencia un `product`) se resuelven mediante JOINs directos sobre
tablas compartidas.

Esta alternativa es tentadora porque es la que ya se conoce (el
desarrollador tiene base sólida en Spring/Spring Boot, pero no
experiencia previa con microservicios en producción) y porque evita,
a corto plazo, varios problemas que con Database-per-Service hay que
resolver explícitamente: transacciones atómicas entre entidades
relacionadas, consultas que cruzan dominios, y la necesidad de un
mecanismo de comunicación entre servicios para acceder a datos que
antes estaban a un JOIN de distancia.

Sin embargo, una base de datos compartida introduce **acoplamiento de
esquema, de despliegue y de rendimiento** entre servicios que se
supone que son independientes:

- Un cambio de esquema en las tablas de `product-service` (renombrar
  una columna, cambiar un tipo de dato) puede romper queries de
  `list-service`, aunque su código no haya cambiado.
- Una migración de base de datos requiere coordinar el despliegue de
  todos los servicios que dependen de esas tablas, eliminando la
  independencia de despliegue que se busca en una arquitectura de
  microservicios.
- Una query pesada de un servicio puede degradar el rendimiento de
  la misma instancia de base de datos usada por otro servicio
  (*noisy neighbor problem*).
- Los JOINs directos entre tablas de distintos dominios facilitan que
  el modelo de datos de un servicio dependa implícitamente del modelo
  interno de otro, erosionando los límites de dominio (*Bounded
  Context*) que los microservicios pretenden establecer.

La decisión sobre el modelo de persistencia debía tomarse antes de
implementar la lógica de negocio de `product-service` y
`list-service`, porque condiciona directamente cómo se diseñan sus
APIs, cómo se relacionan sus entidades, y qué mecanismos de
comunicación son necesarios entre ellos. Esta decisión se tomó de
forma irreversible durante el diseño inicial de la Fase 1, antes del
scaffolding de infraestructura, y este documento formaliza por
escrito una decisión que ya gobernó las sesiones de implementación
posteriores.

## Decisión

Cada microservicio de ShoppingList tiene **su propia base de datos
PostgreSQL, físicamente separada**, y ningún servicio accede
directamente a la base de datos de otro servicio, ni mediante JOINs,
ni mediante conexión directa.

En la implementación actual, esto se traduce en dos instancias
PostgreSQL independientes (`product-db` y `list-db`), cada una en su
propio contenedor Docker, expuestas al host en puertos no estándar
(`5434` y `5435` respectivamente) para evitar conflicto con una
instancia de PostgreSQL preexistente en la máquina de desarrollo.

La única forma en que un servicio obtiene datos que pertenecen a otro
servicio es a través de la **API pública** de ese servicio (REST
síncrono, según las decisiones de comunicación ya establecidas para
el sistema). La base de datos de cada servicio se considera un
**detalle de implementación privado**, no un canal de comunicación
entre servicios.

Como consecuencia directa de esta decisión, las relaciones entre
entidades de distintos servicios no se modelan como claves foráneas
de base de datos, sino como **referencias por identificador** (por
ejemplo, `list_item` almacena el `product_id` y su tipo, `BASE` o
`USER`, sin clave foránea real hacia `product-service`). Cuando se añade un producto a una lista, el sistema persiste localmente el nombre correspondiente mediante el **Snapshot pattern** (campo `display_name` en `list_item`). El valor de este campo se congela en el idioma original (`locale`) de la petición del usuario en ese instante, desacoplando la presentación histórica de la lista de futuros JOINs distribuidos o cambios de nomenclatura en el catálogo de `product-service`.

## Consecuencias

### Positivas

- **Independencia de despliegue real**: `product-service` y
  `list-service` pueden migrar su esquema, escalar su base de datos o
  desplegar una nueva versión sin coordinar con el otro servicio.
- **Encapsulación del modelo de dominio**: cada servicio es dueño
  exclusivo de su modelo de datos. Otros servicios solo conocen el
  contrato expuesto en su API pública, no su estructura interna.
- **Aislamiento de fallos y de carga**: una query costosa o un
  problema de rendimiento en la base de datos de un servicio no
  afecta directamente a la base de datos de otro servicio.
- **Coherencia con la estrategia de comunicación ya definida**: refuerza
  el principio ya establecido de REST síncrono para queries y eventos
  asíncronos para side effects, al eliminar la tentación de resolver
  relaciones cross-domain mediante acceso directo a datos.
- **Escalado independiente**: cada base de datos puede dimensionarse,
  indexarse o migrar a un proveedor distinto sin impacto en el resto
  del sistema (relevante de cara a la ruta de migración hacia AWS RDS).

### Negativas / Trade-offs aceptados

- **Pérdida de transacciones ACID entre servicios**: ya no es posible
  envolver una operación que afecta a `product-service` y a
  `list-service` en una única transacción con `ROLLBACK` garantizado
  por el motor de base de datos. Una operación que requiera
  consistencia atómica entre ambos dominios necesita un patrón de
  coordinación explícito (típicamente, **Saga pattern**).
  - **Deuda técnica aceptada explícitamente**: la implementación de
    Saga pattern y de mecanismos de resiliencia ante fallos de red
    entre servicios (timeouts, retries, circuit breakers — p. ej. con
    Resilience4j) queda **fuera de alcance del MVP actual (Fase 1)**.
    Esta decisión se revisará en un ADR futuro,
    `ADR-XXFX-gestion-de-transacciones-distribuidas-con-saga`
    (nombre provisional, pendiente de número de fase y redacción
    final), que deberá referenciar este documento como el origen de
    la deuda técnica que resuelve.
- **Consistencia eventual en lugar de consistencia inmediata**: cuando
  los datos de un servicio dependen de información replicada o
  sincronizada desde otro (por ejemplo, vía eventos), existe una
  ventana de tiempo en la que ambos servicios no están de acuerdo
  sobre el estado del mundo. Esto es una propiedad inherente al
  patrón, no un defecto puntual.
- **Las queries cross-domain requieren composición en aplicación**:
  consultas que antes serían un JOIN trivial (por ejemplo, "listas que
  contienen productos de la categoría lácteos") requieren ahora
  orquestar llamadas a varios servicios y combinar resultados en
  código de aplicación, con el coste de latencia y complejidad que
  eso implica.
- **Mayor superficie operativa**: más instancias de base de datos que
  mantener, respaldar y migrar (en el estado actual, dos instancias
  PostgreSQL en lugar de una).
- **Necesidad de tolerancia a fallos de red**: las llamadas REST entre
  servicios pueden fallar por causas que una llamada en memoria nunca
  tendría (timeout, servicio caído, latencia de red). El sistema debe
  diseñarse asumiendo que estas llamadas pueden fallar, aunque la
  implementación concreta de esa tolerancia (ver punto de Saga pattern
  y Resilience4j) se posponga deliberadamente.

### Documentación relacionada

- Esta decisión es la causa raíz que motiva el uso del **Snapshot
  pattern** en `list_item.display_name` (`list-service`), documentado
  en su ADR local correspondiente (`list-service/docs/adr/`).
- **Addressed by** (pendiente): `ADR-XXFX-gestion-de-transacciones-distribuidas-con-saga`
  — resolverá la deuda técnica de transacciones distribuidas aceptada
  en este documento. Enlazar bidireccionalmente cuando se redacte.

## Alternativas consideradas

### Shared Database (descartada)

Una única base de datos PostgreSQL compartida entre todos los
microservicios, con JOINs directos entre tablas de distintos dominios.

**Por qué se descartó**: aunque elimina los problemas de consistencia
distribuida y simplifica las queries cross-domain, introduce
acoplamiento de esquema y de despliegue entre servicios que se
consideran incompatibles con el objetivo del proyecto de demostrar
una arquitectura de microservicios real y desplegable, y con el
patrón de comunicación ya decidido (REST síncrono entre servicios en
lugar de acceso compartido a datos).

### Base de datos única con esquemas lógicos separados (descartada)

Una única instancia PostgreSQL con un esquema (`schema`) distinto por
servicio, en lugar de instancias físicamente separadas.

**Por qué se descartó**: aunque reduce el número de instancias a
operar, mantiene un único punto de fallo y un único recurso de
cómputo compartido entre servicios (no resuelve el *noisy neighbor
problem*: una query pesada de `product-service` seguiría compitiendo
por las mismas CPU, memoria y conexiones de la instancia que usa
`list-service`). Tampoco representa con fidelidad cómo se desplegaría
el sistema en un entorno de producción real, donde cada servicio
gestiona su propio motor de base de datos de forma independiente
(incluyendo versión, parámetros de configuración y ciclo de vida de
backups). Se valoró que el ahorro de recursos en Free Tier no
compensaba renunciar a demostrar, en el propio diseño, el aislamiento
real que el patrón Database-per-Service persigue.
