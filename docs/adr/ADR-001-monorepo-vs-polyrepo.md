# ADR-001: Monorepo vs Polyrepo

## Estado
Aceptado

## Contexto

ShoppingList es un sistema de microservicios (Spring Boot + Node.js) con
doble propósito: ser funcional como producto desplegable y servir como
portafolio técnico demostrable en procesos de entrevista.

El proyecto es desarrollado por un único desarrollador, sin equipo, y no
se espera que esto cambie a corto/medio plazo dentro del alcance actual
del proyecto.

Al diseñar la estrategia de repositorio para un sistema con múltiples
servicios independientes (`product-service`, `list-service`, y los que
se incorporarán en fases posteriores: frontend, API Gateway, Config
Service, Auth Service, Notification Service), había que decidir si cada
servicio viviría en su propio repositorio (polyrepo, posiblemente con un
repositorio adicional dedicado a infraestructura/orquestación) o si todo
el sistema viviría en un único repositorio (monorepo).

Esta es la arquitectura "de manual" en sistemas de microservicios de
producción con equipos grandes: cada servicio con su propio ciclo de
vida, su propio repositorio y, normalmente, un repositorio aparte para
la infraestructura compartida (IaC, docker-compose, scripts de
orquestación). Fue la alternativa que generó más dudas antes de
descartarla, precisamente por ser la opción más "canónica".

## Decisión

Se adopta un **monorepo**: un único repositorio Git, con directorio raíz
`shopping-list/`, que alberga todos los microservicios, el frontend y la
infraestructura local (`docker-compose.yml` raíz).

Cada microservicio mantiene independencia operacional *dentro* del
monorepo: su propio sistema de build (`pom.xml` o `package.json`), su
propio `Dockerfile`, sus propios tests y su propio pipeline de CI/CD,
activado mediante path filters de GitHub Actions
(`on: push: paths: ['product-service/**']`).

Se descarta explícitamente adoptar herramientas de tooling de monorepo
dedicado (Bazel, Nx, Turborepo, etc.) — las mismas que usan
organizaciones como Google o Uber para gestionar monorepos a gran
escala. Se opta por un **monorepo ligero**, apoyado únicamente en Git
estándar y path filters nativos de GitHub Actions.

### Por qué monorepo

- **Atomicidad de cambios transversales.** Un cambio que afecta a varios
  servicios a la vez (p. ej., modificar el contrato de un evento en
  `list-service` y actualizar su consumidor en `notification-service`)
  se resuelve en un único PR, revisado y mergeado de forma atómica. En
  polyrepo, el mismo cambio exigiría coordinar dos o más PRs en
  repositorios distintos, con el riesgo de que queden temporalmente
  desincronizados.
- **Coste de CI controlado mediante path filters.** La atomicidad no se
  paga con builds innecesarios: aunque un PR sea transversal, el CI solo
  ejecuta el pipeline de los servicios cuyo path fue modificado. Cada
  servicio conserva un ciclo de CI/CD tan aislado como en un escenario
  polyrepo, evitando el acoplamiento de los tiempos de build.
- **Historial unificado como activo de portafolio.** Un único historial
  de commits permite a un revisor o entrevistador técnico ver la
  evolución del sistema completo, en vez de fragmentos inconexos
  repartidos en varios repositorios.
- **Fricción operativa mínima para demos.** Un único `docker compose up`
  levanta el sistema completo, sin necesidad de clonar y sincronizar
  múltiples repositorios.

### Por qué sin tooling de monorepo dedicado (YAGNI)

Herramientas como Bazel o Nx resuelven problemas de escala que este
proyecto no tiene: miles de módulos, equipos numerosos, builds
distribuidos. Adoptarlas ahora sería sobre-ingeniería (principio YAGNI:
*You Aren't Gonna Need It*) respecto al tamaño y alcance actuales del
proyecto. Mantenerse en Git + path filters aporta:

- Curva de aprendizaje baja.
- Posibilidad de que otro desarrollador colabore sin tener que aprender
  herramientas propietarias de orquestación de monorepos.
- Mantenimiento del pipeline casi nulo.

### Alternativa descartada: Polyrepo + repositorio de infraestructura

Se consideró seriamente un esquema de un repositorio por servicio más un
repositorio adicional para la infraestructura compartida. Es la opción
más alineada con las prácticas habituales de microservicios en
producción con equipos grandes, pero se descarta porque optimiza para un
escenario de escala y equipo distinto al de este proyecto: introduce
sobrecarga de coordinación (múltiples PRs, múltiples repos que
sincronizar) sin que exista, en este contexto, un equipo o una necesidad
de aislamiento de permisos que la justifique.

## Consecuencias

### Positivas

- Refactorizaciones transversales (cambios en contratos de eventos,
  configuración compartida) se gestionan en un único PR.
- Onboarding y demo del sistema completo sin fricción.
- El historial de commits es, en sí mismo, una narrativa legible de la
  evolución del sistema — un activo directo para entrevistas técnicas.
- Pipeline de CI/CD simple de mantener, sin dependencias de tooling
  externo de monorepo.

### Negativas / Trade-offs aceptados

- **Límite de escalabilidad conocido y aceptado conscientemente.** Un
  monorepo sin tooling dedicado degrada su experiencia a medida que
  crecen el número de servicios y, sobre todo, el tamaño del equipo:
  el CI puede volverse más pesado de gestionar globalmente, y los
  permisos por servicio son menos granulares que en repositorios
  separados.
- Dado el alcance actual del proyecto (un solo desarrollador, propósito
  de portafolio, sin previsión de crecimiento a equipo), no se espera
  alcanzar este límite. La decisión se toma con ese límite identificado
  de antemano, no por desconocerlo.
- **Camino de migración si el límite se alcanzara:** si el proyecto
  creciera a un equipo o a un número significativo de servicios, las
  opciones a evaluar en ese momento serían (a) introducir tooling de
  monorepo dedicado (Nx, Turborepo, Bazel) para gestionar la escala sin
  perder la atomicidad de PRs transversales, o (b) migrar a un esquema
  polyrepo, extrayendo cada servicio a su propio repositorio y
  estableciendo un mecanismo de coordinación de cambios transversales
  (versionado de contratos, PRs vinculados, etc.). No se toma esta
  decisión ahora porque hacerlo de forma anticipada sería sobre-
  ingeniería sobre un problema que el proyecto no tiene todavía.
