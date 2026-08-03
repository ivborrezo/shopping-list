# ADR-009: Estrategia de CI y Git Hooks (capa local + capa remota)

## Estado

Aceptado

## Contexto

El proyecto fija su stack de calidad de código en ADR-004 (Checkstyle +
Spotless para Java; ESLint + Prettier para el futuro frontend) y su stack
de testing de integración en ADR-008 (Testcontainers con módulo
PostgreSQL, arrancando un contenedor efímero por test sobre el que se
aplican las migraciones Flyway reales de ADR-007). Ambos ADRs delegan
**explícitamente** la integración de esas herramientas en el pipeline de
CI/CD a un documento aparte:

- ADR-004, Decisión 3: *"La integración concreta en el pipeline de CI/CD
  (orden de ejecución, alcance por fase del roadmap, o si actúan de forma
  bloqueante o no sobre el build) queda fuera de este ADR y se definirá
  en `docs/cicd/cicd-strategy.md`"*.
- ADR-008, Consecuencias / Negativas: *"Requiere Docker disponible en el
  entorno de ejecución del test: en local es transparente (Docker ya es
  prerrequisito del proyecto), pero en CI implica Docker-in-Docker o
  equivalentes en el runner de GitHub Actions. La estrategia concreta
  para resolver esto se decide en `docs/cicd/cicd-strategy.md`
  (pendiente de redactar)"*.

A esos se suma un tercer caveat abierto durante la implementación del
`Dockerfile` multi-stage de `product-service`: el `Dockerfile`
usa BuildKit cache mount (`--mount=type=cache,target=/root/.m2`)
para acelerar builds locales, pero ese cache **no persiste en CI efímero**
sin un backend externo; se anotó `cache-to: type=gha` como
pendiente de configurar en el workflow.

Este ADR cierra las tres delegaciones y caveats en un único documento,
coherente con la regla de timing de ADRs del proyecto: la decisión se
redacta cuando es concreta y cara de revertir, ni antes (especularía
sobre algo no implementado) ni después (perdería el contexto real de
los trade-offs vividos). El momento es el paso 5 de la Rama 2 de
`product-service`: ya existe el primer test de integración real
(`CategoryIntegrationTest`, con Testcontainers singleton + `@ServiceConnection`),
el primer `Dockerfile` multi-stage verificado y el primer slice E2E
(`GET /categories`) en verde, por lo que la estrategia de CI se decide
contra datos reales de coste y latencia, no proyectados.

### Por qué dos capas y no una

El "pipeline" no es un único artefacto que vive en GitHub Actions. La
decisión se estructura en **dos capas con responsabilidades y timing
distintos**:

| Capa | Cuándo corre | Ante quién responde | Latencia esperada | Coste de iterar |
|---|---|---|---|---|
| **Pre-commit hook (local)** | En la máquina del autor, durante `git commit`, antes de que el commit exista | Ante el autor, en tiempo real | Sub-segundo a ~5s | Cada commit |
| **CI GitHub Actions (remoto)** | En runner hosted, después de `git push` | Ante el repo, como gate de fusión | 1-3 min tolerable | Cada push |

Tratar las dos capas como un único pipeline sería mezclar dos problemas
de coste y naturaleza distintos. Este ADR fija el principio de qué hace
cada capa; la materialización operativa (YAML de jobs, orden de steps,
caching, triggers) vive en `docs/cicd/cicd-strategy.md`, mutable sin
reabrir este ADR — misma separación que ADR-004 y ADR-008 ya abrieron
entre decisión de herramienta y estrategia de integración.

## Decisiones

### Decisión 1 — Hook nativo distribuido vía `githooks/` versionado + `core.hooksPath`

El pre-commit hook vive en un directorio `githooks/` versionado en la
raíz del monorepo, activado por `git config core.hooksPath githooks`
(ejecutado una vez por clon y documentado en
`docs/cicd/cicd-strategy.md` §8). El hook viaja con el repositorio:
cualquier evaluador que clone el repo lo recibe sin necesidad de
instalar dependencias adicionales, coherente con el objetivo de
portafolio público del proyecto.

### Decisión 2 — Pre-commit con tres capas y dispatch por servicio

El hook combina tres capas de comprobación, con dispatch por servicio
afectado para evitar arrancar toolchains innecesarias:

1. **Sanity bash** (sub-300ms): reglas mínimas sobre ficheros staged
   que ni CI ni IDE cazan de forma fiable (tabuladores en `.java`/`.js`,
   CRLF no declarado, `console.log`, `.printStackTrace()`, secrets por
   typo). Pure bash, sin invocar ninguna toolchain.
2. **Spotless `:check`** sobre el servicio Java afectado, solo si el
   commit toca ficheros bajo `<servicio>/**` con extensión `.java`.
3. **Checkstyle `:check`** sobre el mismo servicio Java afectado, bajo
   el mismo dispatch.

El dispatch extrae el directorio raíz del servicio Java desde los paths
staged (`git diff --cached --name-only`) y lanza solo la toolchain del
servicio afectado. Si el commit no toca ningún servicio Java (solo
JS, docs, etc.), no arranca JVM. Si el commit toca N servicios Java,
se lanza la toolchain de cada uno. Esto materializa el principio
"monorepo políglota discrimina por servicio" y evita que el coste del
hook escale con el número de servicios del repo.

El coste estimado es de 2-5s por commit que toque Java (startup JVM +
Maven), sub-segundo si no toca Java (solo sanity bash). Con un único
desarrollador y dispatch por servicio, el coste agregado no penaliza
colectivamente. El hook es la capa de feedback inmediato; el gate
definitivo sigue siendo CI (Decisión 5).

Cuando en el futuro entren servicios JS (`notification-service`,
frontend React), el dispatch bifurca a `npm` / ESLint / Prettier en
vez de Maven/Spotless/Checkstyle. La materialización concreta de la
toolchain JS es **decisión diferida de implementación**, no ADR:
este ADR fija el principio "dispatch por servicio, toolchain según
lenguaje", coherente con el alcance global políglota del documento.

### Decisión 3 — Solape deliberado (red doble) selectiva entre hook y CI

Se aplica **red doble deliberada para quality gates baratos en local
(formato/estilo sobre ficheros staged), y capas separadas para gates
caros (tests Testcontainers, compile de módulo completo)**:

- **Solapadas deliberadamente (hook + CI):** Spotless y Checkstyle.
  Feedback inmediato en commit + gate definitivo en CI. La red doble
  se justifica porque `--no-verify` bypassa el hook pero no CI; el
  bypass sales caro (fallos a los 30-60s en push, visible en el PR).
- **No solapadas (capas complementarias):**
  - Sanity bash: solo en hook (caza lo que ni CI ni IDE ven).
  - Compile + tests Testcontainers: solo en CI. Levantar Postgres por
    commit es prohibitivo en un hook de feedback inmediato.

No es dogma transversal: la red doble se aplica donde el coste/latencia
lo justifica, no a todas las comprobaciones por igual.

### Decisión 4 — Triggers del workflow por paths del servicio + compartidos + el propio workflow

El workflow de `product-service` se dispara con:

```
paths:
  - product-service/**
  - config/checkstyle/**
  - .github/workflows/product-service.yml
```

Tres motivos distintos para los paths no-service:

- **`config/checkstyle/**` (correctness):** un cambio del ruleset
  compartido puede romper el build de cualquier servicio que lo use.
  Revalidarlo evita que el fallo se descubra tardíamente cuando un
  commit no relacionado toque el servicio días después. Anclado en
  incidente real del proyecto (Rama 1: el stock de checkstyle 9.3
  dejó de incluir `RegexpHeader`, descubierto solo al ejecutar).
- **`.github/workflows/product-service.yml` (self-validation):** un
  cambio del propio workflow debe re-ejecutarse para validar la
  modificación; sin eso, un typo en el YAML se descubre en un push sin
  relación.

El principio que este ADR fija es "paths del servicio + compartidos
relevantes + el propio workflow"; la lista concreta de paths
compartidos evoluciona con el monorepo (hoy `config/checkstyle/**`;
futuros compartidos Spotless u otros se añadirán a la lista cuando
existan).

### Decisión 5 — Gate híbrido: advisory temporal → hard gate por estabilidad observada

El check de CI arranca en modo **advisory** (se ejecuta y se ve en el
PR/commit, pero no bloquea el merge a `main`) y se **auto-promueve a
hard gate** (branch protection de `main` con required status check)
tras observar estabilidad del pipeline.

**Criterio de promoción: estabilidad observada**, no tiempo calendario.
El objetivo del período advisory es estabilizar el pipeline, no cumplir
un plazo. Los riesgos concretos que motivan el período, anclados en el
stack real del proyecto:

- Rate limit de Docker Hub para `postgres:16-alpine` en runners
  efímeros sin cache de imagen.
- Timeout de Testcontainers en runners efímeros la primera vez que
  arranca el contenedor.
- Inestabilidad del nombre del check durante la iteración del workflow
  (cambios de job_id mientras se estabiliza la estructura del YAML).

Si el pipeline sigue flaky pasado el plazo estimado, aplazar el hard
gate es correcto, no incumplimiento. La deuda técnica del período
advisory (main sin protection temporal) es explícita: para un repo
portafolio con un único desarrollador el riesgo es bajo (sin
contributors externos que abran PRs), y se cierra por la auto-promoción
a hard gate, no requiere ADR separado.

### Decisión 6 — Testcontainers en CI sobre `ubuntu-latest` hosted (Docker nativo del runner, no DinD real)

El workflow corre en `ubuntu-latest` (GitHub-hosted runner), **sin
`jobs.<id>.container:`** y **sin `services:`** del workflow. Testcontainers
usa Docker disponible nativamente en el runner Linux hosted (vía
docker.sock del runner, no un contenedor dentro de contenedor con
`--privileged`). Esta es la aclaración técnica que cierra el
"Docker-in-Docker en CI a vigilar" de ADR-008: en runners Linux hosted
no es DinD en sentido estricto (no hay contenedor dentro de contenedor
con `--privileged`), es "docker disponible en el runner".

**Paridad estricta local↔CI:** el mismo `CategoryIntegrationTest` que
arranca el contenedor singleton en local corre idéntico en CI, mismo
wiring vía `@ServiceConnection`, sin bifurcaciones por entorno. No
haya un perfil de test específico de CI ni overrides del
datasource en el workflow. Coherente con el "mismo ciclo que producción"
de ADR-008: la CI reproduce el test tal cual existe, no una variante
adaptada al runner.

**Escalado por duplicación, no por bifurcación.** Cada servicio añadido
al monorepo replica su propio workflow con su propio runner aislado y
su propio contenedor Testcontainers. Ajustes futuros puntuales (rate
limit Docker Hub, cache de imagen PostgreSQL, ajustes de timeout) van
en `docs/cicd/cicd-strategy.md`, no reabren este ADR.

### Decisión 7 — Caching Maven + BuildKit `type=gha,mode=max`

1. **Maven**: `actions/setup-java@v4` con `cache: maven` sobre
   `~/.m2/repository`, invalidación por hash del `pom.xml`. Salta el
   download de dependencias desde Maven Central en cada run. Estándar,
   coste de configuración trivial, ahorro estimado 30-60s/run.

2. **BuildKit cache para Docker**: en el step de `docker build` del
   workflow, `cache-to/cache-from: type=gha,mode=max` reutiliza las
   capas del stage builder (descarga de dependencias Maven dentro del
   contenedor + compilación del jar) entre runs vía GitHub Actions
   cache backend (límite 10 GB por repo, holgado para el monorepo
   actual). Requiere `# syntax=docker/dockerfile:1.4`, ya presente en
   el `Dockerfile` de `product-service` desde la Rama 1. Cierra el
   caveat "cache mount no persiste en CI efímero sin backend externo"
   anotado durante la implementación del `Dockerfile` multi-stage en
   la Rama 1.

Como consecuencia de activar caching de Docker, el workflow incluye un
step de `docker build` (sin push de imagen, sin despliegue) que valida
que el `Dockerfile` construye en cada push. Sin este step, regresiones
del `Dockerfile` se detectarían tarde (Fase 3 al desplegar); el smoke
manual del paso 8 de la Rama 1 verificó el `Dockerfile` una vez a mano,
pero sin reproducirse en CI, cualquier cambio del `Dockerfile` queda
sin validación automática.

### Decisión 8 — Aislamiento Surefire/Failsafe fuera de alcance (criterio de revisita)

Hoy todos los tests corren bajo Surefire en `mvn verify`, sin
separación entre unitarios e integración. No existen tests unitarios
aislables dignos de separación todavía (solo `CategoryIntegrationTest`
de integración con Testcontainers). Configurar infraestructura
Surefire/Failsafe + dos steps de workflow para una sola clase es
prematuro.

Este ADR deja la separación **fuera de alcance de forma explícita**, con
criterio de revisita: cuando aparezcan tests unitarios aislables dignos
de separación con DATOS reales (p. ej. tests de lógica puro que se vean
penalizados por el arranque del contenedor PostgreSQL singleton). El detonante natural
para reconsiderar es la aparición de tests rápidos (<500ms) penalizados
por el arranque del contenedor.

**Convención de naming adoptada como micro-decisión de implementación**
(no ADR): futuros tests unitarios se nombrarán `*Test`, los de
integración con Testcontainers seguirán `*IT` o `*IntegrationTest`
(como ya hace `CategoryIntegrationTest`). Cuando llegue el momento de
separar plugins, los nombres ya estarán alineados y la migración a
Failsafe será trivial.

## Consecuencias

### Positivas

- **Feedback inmediato en commit:** los quality gates baratos
  (Spotless, Checkstyle) se ejecutan en local en 2-5s, evitando un
  cycle de ida-vuelta a CI por fallo de formato. El sanity bash caza
  además patrones que ni CI ni IDE detectan de forma fiable.
- **Defense-in-depth de secrets en dos capas complementarias:** el
  sanity bash del hook caza typos locales (`password =`, `secret =`
  obvios) antes del commit; GitHub Secret Scanning + push protection
  (nativo, gratuito en repositorios públicos, activado desde
  Settings → Code security) caza patrones reales de tokens (AWS keys,
  GitHub tokens, Stripe keys, etc.) del lado servidor con una base de
  datos de patrones que un regex bash simple no tiene. El sanity bash
  cubre el caso "antes del commit"; GitHub Secret Scanning cubre el
  caso "`--no-verify` o clon sin hook": defense-in-depth correcta, no
  redundancia.
- **Gate definitivo en CI:** los jobs de Checkstyle/Spotless en el
  workflow **fallan el build** (no advisory), porque si CI fuera
  advisory se vaciaría el gate cuando un contributor externo clone el
  repo sin el hook instalado. `--no-verify` existe en Git para puentear
  el hook local, pero CI no se puede puentear desde un commit.
- **Paridad local↔CI estricta en tests:** el mismo
  `CategoryIntegrationTest` corre idéntico en ambos entornos
  (`@ServiceConnection`, sin perfil de test específico de CI). El
  "mismo ciclo que producción" de ADR-008 se preserva en CI.
- **Cierre de caveats pendientes:** ADR-008 ("Docker-in-Docker en CI a
  vigilar" + "gate de calidad en CI"), ADR-004 ("integración concreta
  en pipeline… pendiente de definir en cicd-strategy.md") y el caveat
  "cache-to: type=gha pendiente" anotado en la Rama 1 sobre el
  `Dockerfile` multi-stage quedan resueltos por este ADR y
  su materialización operativa en `docs/cicd/cicd-strategy.md`.
- **Escalado por duplicación:** cada servicio añadido al monorepo
  replica el patrón (su propio workflow, su propio hook dispatch, su
  propio contenedor Testcontainers). El coste de añadir `list-service`
  o `notification-service` no bifurca el diseño, lo duplica.

### Negativas / Trade-offs aceptados

- **Startup JVM de 2-5s por commit que toque Java.** Coste asumido por
  el autor a cambio de red de seguridad. Reconocido honestamente: si
  el proyecto creciera a un equipo multimódulo, el coste agregado sí
  penalizaría colectivamente; ese caso extremo no aplica hoy y se
  reevaluará si aparece.
- **Deuda interim de `main` sin protección durante el período advisory.**
  Para un repo portafolio con un único desarrollador el riesgo es bajo
  (sin contributors externos que abran PRs). La deuda se cierra por la
  auto-promoción a hard gate (Decisión 5), no requiere ADR separado.
- **Configuración manual `core.hooksPath` por clon.** Un comando
  (`git config core.hooksPath githooks`) ejecutado una vez por clon y
  documentado en `docs/cicd/cicd-strategy.md` §8. Coste bajo, no se
  automatiza con dependencias externas (Husky) para no añadir tooling
  JS al monorepo mayoritariamente Java.
- **Latencia de arranque de contenedor Postgres por run de CI.** La
  primera vez que Testcontainers arranca `postgres:16-alpine` en un
  runner efímero paga el pull de imagen; runs siguientes con cache de
  imagen (futuro, en `cicd-strategy.md`) la reducen. No bloquea el
  pipeline, lo alarga ~10-30s en el peor caso.

## Alternativas consideradas

### Decisión 1 — Mecánica de instalación del hook

**Husky (descartada).** No se descarta por ser mala herramienta, sino
porque añade una dependencia JS al monorepo solo para automatizar el
"paso extra al clonar" que `core.hooksPath` documentado resuelve sin
dependencias. El monorepo es hoy mayoritariamente Java (`product-service`
+ futuro `list-service`); añadir tooling JS en la raíz solo para
gestionar hooks es desproporcionado. Husky además arrastra
`package.json`, `husky/` con su propio init, y convención de scripts
que mezclaría ecosistemas. Trade-off real, no desprecio genérico: Husky
 sería la elección razonable en un monorepo JS-nativo.

**`.git/hooks/` manual (descartada).** El hook vive en `.git/hooks/pre-commit`,
no versionado. Simple, pero se pierde al clonar o recetar. Incompatible
con el objetivo de portafolio público: cualquier evaluador que clone el
repo no lo recibe, y el repo público no evidencia que existe CI local.

### Decisión 2 — Contenido del pre-commit

**Solo sanity bash barato (descartada).** Asume un cycle de ida-vuelta
a CI por fallo de formato que, en el contexto de un solo desarrollador
+ dispatch por servicio, es más costoso que los 2-5s de hook por commit
con formato. La startup JVM no es prohibitiva en este stack con dispatch
por path. Se consideró y se descartó con datos reales del proyecto, no
en abstracto.

**Hook sobre todo el monorepo sin dispatch (descartada).** El coste
crece con el número de servicios: cada commit que toque cualquier
servicio pagaría la toolchain de todos. El dispatch por servicio
(eliminando servicios no afectados) es lo que hace que el coste del hook
no escale con el tamaño del monorepo.

### Decisión 3 — Solape hook↔CI

**Sin solape (descartada).** Coherentemente al reincorporar
Spotless/Checkstyle al hook en Decisión 2, el solape no es opcional: la
decisión #3 deriva de #2, no es independiente. La formulación
"selectiva" (red doble para quality barato, capas separadas para gates
caros) es lo que queda tras descartar las dos opciones extremas (todo
solapado, nada solapado).

### Decisión 4 — Trigger paths

**Solo paths del servicio (descartada).** Riesgo real de "cambio del
ruleset silencioso → fallo tardío" anclado en el incidente de checkstyle
9.3 de la Rama 1: el stock de esa versión dejó de incluir
`RegexpHeader`, descubierto solo al ejecutar. Esperar a que un commit
no relacionado tocara `product-service/**` días después para detectar
el fallo es un patrón de debugging caro.

**Workflow separado para paths compartidos (descartada).** Sobre-ingeniería
para un solo servicio. La complejidad de mantener dos workflows (uno
por servicio + uno transversal para `config/checkstyle/**`) no se
justifica hoy. Re-evaluable si el monorepo crece y los falsos positivos
de CI por tocar paths compartidos se vuelven frecuentes.

### Decisión 5 — Gate policy

**Hard gate desde el primer día (descartada).** Branch protection en
`main` con required status check desde el primer run. Riesgos reales
de infra (rate limit Docker Hub, timeout Testcontainers en CI efímero,
inestabilidad del nombre del check durante iteración del workflow)
bloquearían el flujo que hoy funciona sin PR abierto. El período
advisory existe para estabilizar el pipeline, no para postergar la
decisión de gatear.

**Advisory indefinido (descartada).** El check se ejecuta y se ve,
pero no bloquea nunca. Compromete el valor del gate de portfolio: un
evaluador que ve `main` con merges recientes y checks rojos concluye
"este repo no tiene CI realmente". El hard gate final es obligatorio
para el objetivo del proyecto; la pregunta es cuándo se activa, no si.

### Decisión 6 — Testcontainers en CI

**Service container `postgres:16-alpine` de GitHub Actions (descartada).**
`services:` del workflow levanta un contenedor Postgres accesible por
el job vía variables de entorno inyectadas. Más simple que Testcontainers,
sin biblioteca Java, pero **rompe paridad local↔CI**: en local el test
arranca el contenedor Testcontainers con `@ServiceConnection`; en CI
usaría un wiring distinto (variables del service container). Desdibuja
el "mismo ciclo que producción" de ADR-008: en CI ya no se reproduciría
el patrón de Testcontainers, se usaría un mecanismo distinto.

Adicionalmente, escala por **bifurcación aditiva**: cada servicio
añadido (3 servicios → 3 wirings distintos por variables de entorno en
el workflow) introduce una bifurcación de configuración, frente a la
duplicación limpia del patrón Testcontainers (cada servicio replica el
mismo patrón con `@ServiceConnection`, sin configuración específica de
workflow).

### Decisión 7 — Caching

**Solo Maven sin Docker cache (descartada).** Más simple, pierde
~60-90s/run de rebuild Docker desde cero. En Fase 1 sin CD no duele
mucho, pero el caveat "cache-to: type=gha" anotado en la Rama 1
sobre el `Dockerfile` multi-stage quedaría abierto. No dejar deuda
diferida.

### Decisión 8 — Aislamiento Surefire/Failsafe

**Separar Surefire/Failsafe ya (descartada).** Configurar dos plugins
+ dos steps de workflow para una sola clase de test (`CategoryIntegrationTest`)
no se justifica con datos reales del proyecto hoy. Sobre-ingeniería
prematura. El detonante natural para reconsiderar es la aparición de
tests rápidos (<500ms) penalizados por el arranque del contenedor
PostgreSQL singleton; cuando aparezcan, los nombres ya alineados
(`*Test`/`*IT`/`*IntegrationTest`) harán la migración a Failsafe
trivial.

## Documentación relacionada

- **Cita a ADR-004** (Estándares de Desarrollo y Gobernanza): la
  Decisión 3 de ADR-004 fija Checkstyle + Spotless + ESLint + Prettier
  como herramientas, y deja explícitamente fuera la integración en el
  pipeline ("se definirá en `docs/cicd/cicd-strategy.md`"). Este ADR
  cierra esa delegación para la estrategia de CI (no reabre la
  elección de herramienta).
- **Cita a ADR-008** (Testcontainers para testing de integración): la
  Decisión 6 de este ADR cierra el caveat "Docker-in-Docker en CI a
  vigilar" de ADR-008, y la Decisión 5 cierra el "gate de calidad en
  CI" que ADR-008 marcaba como pendiente en `docs/cicd/cicd-strategy.md`.
- `docs/cicd/cicd-strategy.md` — Materialización operativa de las
  decisiones de este ADR (YAML de jobs, orden de steps, caching, gating,
  hook policy resumida). Documento mutable, evoluciona sin reabrir este
  ADR.
