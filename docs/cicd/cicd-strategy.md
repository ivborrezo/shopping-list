# Estrategia de CI/CD del monorepo ShoppingList

## 1. Propósito

Este documento es la **materialización operativa** de las decisiones
fijadas en [ADR-009](../adr/ADR-009-estrategia-de-ci-y-git-hooks.md).
Mientras el ADR captura decisiones con trade-offs irreversibles o
caros de revertir (cómo se instalan los hooks, qué solapa con qué,
cómo se gatea la fusión, cómo se ejecuta Testcontainers en CI), este
documento describe cómo se materializan esas decisiones en el
workflow YAML y en el script del hook.

Es **documentación operativa humana** (Markdown, no interpretado por
ningún programa) y **muta cada vez que el pipeline cambia de forma no
trivial**. Es coherente con la misma separación que ADR-004 y ADR-008
abrieron entre decisión de herramienta y estrategia de integración:
ajustar el grado de exigencia, el orden de steps o el caching no
reabre ADR-009.

## 2. Trigger policy

El workflow de cada servicio se dispara con:

```yaml
on:
  push:
    paths:
      - <servicio>/**
      - config/checkstyle/**
      - .github/workflows/<servicio>.yml
  workflow_dispatch:
```

Tres motivos para los paths no-service (justificación detallada en
ADR-009, Decisión 4):

- `<servicio>/**` — el servicio afectado.
- `config/checkstyle/**` — ruleset compartido; un cambio puede romper
  cualquier servicio que lo use.
- `.github/workflows/<servicio>.yml` — self-validation: un cambio del
  propio workflow debe re-ejecutarse para validar la modificación.

`workflow_dispatch` permite reproducir un run concreto desde la web
(sin push), útil para depurar un fallo puntual disparando el workflow
a mano sobre una rama o un commit.

El trigger es `on: push` (no `on: pull_request`) porque el flujo de
trabajo del proyecto es `push` periódico sin PR abierto durante la
rama feature, con PR de cierre al fusionar. El check corre en cada
push de cada rama.

## 3. Job por servicio

Un workflow por servicio (`product-service.yml`, futuro
`list-service.yml`, etc.), cada uno corriendo en su propio runner
aislado `ubuntu-latest` (GitHub-hosted). No se comparten runners entre
servicios en el mismo run: si un commit toca dos servicios
simultáneamente, se disparan dos workflows en runners distintos,
cada uno con su propio contenedor Testcontainers.

```yaml
jobs:
  build:
    runs-on: ubuntu-latest
```

Sin `jobs.<id>.container:` (el job corre directamente en el runner,
no dentro de un contenedor). Sin `services:` del workflow (ver §7).

## 4. Orden de steps

El orden dentro del job sigue el principio fail-fast: las
comprobaciones baratas van primero para no gastar ciclos en
comprobaciones caras (Testcontainers, compile) si las baratas ya
fallan.

```yaml
steps:
  - uses: actions/checkout@v4
  - uses: actions/setup-java@v4
    with:
      distribution: temurin
      java-version: 21
      cache: maven
  - name: Spotless check
    run: ./product-service/mvnw -f product-service/pom.xml spotless:check
  - name: Checkstyle check
    run: ./product-service/mvnw -f product-service/pom.xml checkstyle:check
  - name: Compile
    run: ./product-service/mvnw -f product-service/pom.xml -q compile
  - name: Verify (Testcontainers)
    run: ./product-service/mvnw -f product-service/pom.xml clean verify
  - name: Docker build (valida Dockerfile)
    run: docker build -t shopping-list/product-service:ci product-service/
    env:
      DOCKER_BUILDKIT: 1
```

Justificación del orden:

- **Spotless antes que Checkstyle** (igual que en local): el formatter
  normaliza, el linter valida lo que queda. Si Spotless falla, ni
  gastamos un ciclo en Checkstyle.
- **Checkstyle antes que compile**: Checkstyle no requiere compilar
  (ruleset basado en checkstyle 9.3, sin módulos que dependan de
  tipos). Fallar en Checkstyle es más barato que fallar en compile.
- **Compile antes que verify**: si el código no compila, no tiene
  sentido levantar Postgres. Compile valida tipos; verify ya
  incluye compile, pero un step de compile aislado da fail-fast
  limpio antes del paso caro.
- **Verify al final (con Testcontainers)**: lo más caro del workflow.
  Levanta un contenedor PostgreSQL efímero, aplica migraciones
  Flyway, carga el contexto de Spring, corre los tests.
- **Docker build (validación de Dockerfile)**: sin push de imagen, sin
  despliegue. Valida que el `Dockerfile` construye en cada push. Sin
  este step, regresiones del `Dockerfile` se detectarían tarde (Fase 3
  al desplegar); el smoke manual del paso 8 de la Rama 1 verificó el
  `Dockerfile` una vez a mano, pero cualquier cambio posterior
  queda sin validación automática sin este step.

Cada step es bloqueante (no se continúa si el anterior falla). El
orden es directo: si falla Spotless, no corre Checkstyle, no compila,
no prueba, no construye imagen. Los jobs de Checkstyle y Spotless
fallan el build (no advisory) — ver §5.

### 4.1 Umbral de severidad de Checkstyle

El `pom.xml` de cada servicio Java fija `violationSeverity=error` en
`maven-checkstyle-plugin` (solo las violaciones de severity `error`
fallan el build). El `config/checkstyle/checkstyle.xml` compartido
en la raíz del monorepo es una copia física del stock
`google_checks.xml` de checkstyle 9.3 (copia física desde la Rama 1,
ver ADR-004 §Decisión 3) con el root `Checker` dejando `severity=
warning` como default — lo que heredan todos los módulos que no lo
sobreescriben. Sobre ese default, 35 módulos se elevan explícitamente
a `severity=error`, agrupados en tres buckets según el motivo de la
elevación:

- **Bucket A — bug-prone / estructural (12 módulos):** reglas que
  atrapan defectos semánticos que Spotless no puede reformatear
  (`OuterTypeFilename`, `IllegalTokenText`,
  `AvoidEscapedUnicodeCharacters`, `AvoidStarImport`,
  `OneTopLevelClass`, `EmptyBlock`, `NeedBraces`,
  `MissingSwitchDefault`, `FallThrough`, `UpperEll`, `ModifierOrder`,
  `NoFinalizer`).
- **Bucket B — naming conventions (14 módulos):** reglas de nombres
  que Spotless no toca (`PackageName`, `TypeName`, `MemberName`,
  `ParameterName`, `LambdaParameterName`, `CatchParameterName`,
  `LocalVariableName`, `PatternVariableName`,
  `ClassTypeParameterName`, `RecordComponentName`,
  `RecordTypeParameterName`, `MethodTypeParameterName`,
  `InterfaceTypeParameterName`, `MethodName`).
- **Bucket C — javadoc (9 módulos):** reglas de documentación
  coherentes con AGENTS.md §5 (Javadoc obligatorio en tipos
  públicos y métodos públicos ≥2 líneas). `NonEmptyAtclauseDescription`,
  `InvalidJavadocPosition`, `SummaryJavadoc`, `JavadocParagraph`,
  `RequireEmptyLineBeforeBlockTagGroup`, `AtclauseOrder`,
  `JavadocMethod`, `MissingJavadocMethod`, `MissingJavadocType`.

El resto de módulos del ruleset (~22) sigue como `warning` heredado
del root `Checker`: formatting cubierto por Spotless (red doble
deliberada, ADR-009 §3) y reglas subjetivas o ruidosas
(`AbbreviationAsWordInName`, `OverloadMethodsDeclarationOrder`,
`VariableDeclarationUsageDistance`, `Indentation`, `LineLength`,
etc.) que no merecen bloquear el build.

Ajustar el umbral (añadir o quitar módulos del bucket de error) no
reabre ADR-009: el ADR fija el principio ("Checkstyle debe fallar el
build, no advisory") y esta subsección es su materialización
operativa. ADR-004 delegó explícitamente la integración concreta del
grado de exigencia a este documento (§Decisión 3, "ajustar el grado
de exigencia de un gate de CI es bajo comparado con el de cambiar la
herramienta"). `list-service` heredará automáticamente el ruleset
compartido por path relativo desde su pom.

## 5. Gating

### Estado actual: advisory

El check de CI arranca en modo **advisory**: se ejecuta y se ve en el
PR/commit, pero no bloquea el merge a `main`. Motivado por los
riesgos de infra anclados en el stack real (rate limit Docker Hub para
`postgres:16-alpine`, timeout de Testcontainers en runners efímeros
sin cache de imagen, inestabilidad del nombre del check durante la
iteración del workflow).

### Criterio de promoción a hard gate

**Estabilidad observada**, no tiempo calendario. Tras observar los N
primeros runs consecutivos en verde en el rango de variabilidad
esperado (sin flakiness por rate limit o timeout), se configura branch
protection en `main` con required status check:

```
Settings → Branches → Branch protection rules → main
  ☑ Require status checks to pass before merging
  Status checks: build (o el job_id estabilizado)
```

Si el pipeline sigue flaky pasado el plazo estimado, aplazar el hard
gate es correcto, no incumplimiento. El objetivo del período advisory
es estabilizar el pipeline, no cumplir un plazo.

### Deuda técnica explícita

Durante el período advisory, `main` queda temporalmente sin
protection. Para un repo portafolio con un único desarrollador el
riesgo es bajo (sin contributors externos que abran PRs). La deuda se
cierra por la auto-promoción a hard gate, no requiere ADR separado.

### Gate de quality en CI (no advisory)

Los steps de Checkstyle y Spotless en el workflow **deben fallar el
build** (no advisory), porque si CI fuera advisory se vaciaría el gate
cuando un contributor externo clone el repo sin el hook instalado.
`--no-verify` existe en Git para puentear el hook local, pero CI no se
puentea desde un commit. Por eso la layer CI no es opcional ni
advisory para quality, solo lo es (temporalmente) el gate de merge
hasta estabilizar infra.

## 6. Caching

### 6.1 Maven

`actions/setup-java@v4` con `cache: maven` sobre `~/.m2/repository`,
con invalidación por hash del `pom.xml`. Sin invocar `actions/cache`
directamente: `setup-java` con `cache: maven` hace lo mismo. Salta el
download de dependencias desde Maven Central en cada run.

### 6.2 Docker BuildKit cache

El step de `docker build` del workflow usa `cache-to/cache-from: type=gha,mode=max`
para reutilizar capas del stage builder entre runs vía GitHub Actions
cache backend (límite 10 GB por repo, holgado para el monorepo).

```yaml
- name: Docker build (valida Dockerfile)
  uses: docker/build-push-action@v5
  with:
    context: product-service/
    push: false
    cache-from: type=gha
    cache-to: type=gha,mode=max
```

El `Dockerfile` ya incluye `# syntax=docker/dockerfile:1.4` (Rama 1)
y `--mount=type=cache,target=/root/.m2` para el cache mount del stage
builder. Con `type=gha`, ese cache mount persiste entre runs de CI
vía el backend de GitHub Actions cache.

Próximamente, valorar cache de imagen `postgres:16-alpine` si el
rate limit de Docker Hub se vuelve un problema (estrategia futura:
`type=registry` o pull vía ghcr.io como mirror).

## 7. Resolución Docker/Testcontainers en runner hosted

Aclaración técnica que cierra el "Docker-in-Docker en CI a vigilar"
de ADR-008.

`ubuntu-latest` (GitHub-hosted runner Linux) trae Docker pre-instalado
y corriendo en modo root. Testcontainers lanza contenedores Postgres
usando el docker.sock del runner — **no es DinD en sentido estricto**
(no hay contenedor dentro de contenedor con `--privileged`), es
"docker disponible en el runner".

Consecuencias prácticas:

- **Sin `services:` del workflow** (no se levanta Postgres como
  service container; Testcontainers levanta el suyo propio desde el
  test). La sección §4 de ADR-009 explica por qué: usar `services:`
  rompe paridad local↔CI y escala por bifurcación aditiva.
- **Sin `jobs.<id>.container:`** (el job no corre dentro de un
  contenedor).
- **Sin flags `--privileged`** en el runner. Docker está disponible
  por defecto.

Testcontainers arranca el contenedor singleton (estático por JVM/Surefire,
patrón `static final PostgreSQLContainer<?>` + `@Container` compartido
en el JVM) con `@ServiceConnection` para wiring automático con Spring.
El mismo `CategoryIntegrationTest` que funciona en local corre
idéntico en CI, sin profile de test específico, sin overrides del
datasource en el workflow.

## 8. Hook policy

Resumen ejecutivo de la estrategia de hooks (trade-offs y justificación
detallada en ADR-009).

### Instalación

El hook vive en `githooks/pre-commit` versionado en la raíz del
monorepo. Activación por clon:

```
git config core.hooksPath githooks
```

La carga de variables de entorno del servicio (datasource, puertos)
se redactará en `<servicio>/docs/local-setup.md`, ya que es
específica de cada servicio. Para `product-service`, ese documento
se redacta en el paso 8 de la Rama 2
(`docs(product-service): redactar local-setup.md con variables de entorno y source .env`),
una vez que todo lo anterior (wiring en `docker-compose.yml` incluido)
esté verificado y se pueda documentar con precisión.

### Contenido del hook

Tres capas con dispatch por servicio:

1. Sanity bash (sub-300ms): reglas mínimas (tabuladores en `.java`/`.js`,
   CRLF no declarado, `console.log`, `.printStackTrace()`, secrets por
   typo). Pure bash sin invocar toolchains.
2. Spotless `:check` sobre el servicio Java afectado, solo si el commit
   toca ficheros bajo `<servicio>/**` con extensión `.java`.
3. Checkstyle `:check` sobre el mismo servicio Java afectado, mismo
   dispatch.

Si el commit no toca ningún servicio Java, no arranca JVM (solo sanity
bash). En el futuro, si el commit toca servicios JS
(`notification-service`, frontend React), el dispatch bifurca a `npm` /
ESLint / Prettier. La materialización concreta de la toolchain JS es
decisión diferida de implementación: este documento se ampliará cuando
el primer servicio JS entre en el hook.

### Hook commit-msg (Conventional Commits)

Otro hook en `githooks/commit-msg`, activado por el mismo
`core.hooksPath githooks` de la Decisión 1 del ADR-009, sin
configuración adicional. Valida que el mensaje de commit cumple la
convención del proyecto `tipo(scope): resumen` en castellano con una
regex mínima en bash puro (sub-300ms, mismo principio "sanity bash
para lo que ni CI ni IDE cazan" del ADR-009).

Tipos válidos: `chore`, `build`, `feat`, `test`, `docs`, `ci`, `fix`,
`refactor`. Scope opcional, pero si aparece debe corresponderse con
el servicio o módulo afectado (p. ej. `(product-service)`, `(adr)`,
`(docs)`). La regex exacta y la lógica de dispatch por scope son
micro-decisiones de implementación del paso 6, no se fijan aquí.

No se valida en CI todavía: añadir commitlint (Node) al workflow
arrastraría tooling Node al monorepo mayoritariamente Java, mismo
trade-off descartado con Husky en la Decisión 1 del ADR-009. El hook
`commit-msg` es capa suficiente hoy (un solo desarrollador;
`--no-verify` existe en Git). Revisitable si el repo adquiere
contributors externos — apuntado en §11 (Out of scope).

### Solape con CI

Spotless y Checkstyle están solapados con CI de forma deliberada (red
doble: `--no-verify` bypassa el hook, pero CI no). Sanity bash es solo
local (caza lo que ni CI ni IDE ven). Compile y Testcontainers son solo
CI (prohibitivos en un hook de feedback inmediato).

### Configuración manual por clon

Tras clonar el repositorio, ejecutar una vez por clon:

```bash
# Activar githooks versionados (ADR-009, Decisión 1)
git config core.hooksPath githooks
```

Verificar que los hooks están activos:

```bash
ls githooks/
# Debe listar: pre-commit  commit-msg
```

No se automatiza con dependencias externas (Husky) para no añadir
tooling JS al monorepo mayoritariamente Java. Coste bajo: un solo
comando tras el clone, sin dependencias adicionales.

## 9. Convención de naming de tests

Adoptada como micro-decisión de implementación (no ADR):

- **Tests unitarios** futuros: `*Test` (p. ej. `LocaleResolverTest`,
  `CategoryServiceTest`).
- **Tests de integración** con Testcontainers: `*IT` o
  `*IntegrationTest` (como ya hace `CategoryIntegrationTest`).

Los nombres ya están alineados, de forma que la migración futura a
Failsafe (separar unitarios de integración) será trivial cuando se
justifique.

**Sin configuración de Surefire/Failsafe hoy**: todos los tests corren
bajo Surefire en `mvn verify`. Pendiente de activar la separación
cuando aparezcan tests unitarios aislables dignos de ella (ver ADR-009,
Decisión 8).

## 10. Matriz de servicios afectados

| Servicio | Lenguaje | Workflow | Contenedor en CI | Hook |
|---|---|---|---|---|
| `product-service` | Java | `product-service.yml` ✅ | Testcontainers Postgres + docker build | Dispatch Java (Maven/Spotless/Checkstyle) |
| `list-service` | Java | (futuro) | (replicará patrón product-service) | Dispatch Java (mismo patrón) |
| `notification-service` | JS/Node.js | (futuro Fase 5) | Sin BD propia hoy (ADR-008); re-evaluable | Dispatch JS (npm/ESLint/Prettier) |
| `frontend` (React) | JS | (futuro Fase 2) | Sin contenedor | Dispatch JS (npm/ESLint/Prettier) |

Cómo se añaden nuevos servicios:

1. Crear `.github/workflows/<servicio>.yml` replicando el patrón del
   servicio Java existente (si es Java) o adaptando para la toolchain
   del lenguaje (si es JS).
2. Actualizar `paths` del trigger según paths del servicio + paths
   compartidos relevantes (añadir a `config/checkstyle/**` otros
   compartidos que aparezcan, p. ej. si el monorepo añade una
   config ESLint compartida en el futuro).
3. Documentar en la matriz de esta sección.

## 11. Out of scope explícito

Lo que **no** cubre esta estrategia, coherente con ADR-009:

- **CD (continuos deployment):** no hay target de despliegue todavía
  (`product-service` no está ni integrado en `docker-compose.yml`
  raíz — es el paso 7 de Rama 2; AWS Fargate llegaría en Fase 3
  o posterior). Definir ahora si el deploy es continuo/gated/manual
  es especular sobre infraestructura inexistente. Revisitar cuando
  exista target de despliegue (AWS o equivalente).
- **Matrix cross-JDK:** el proyecto fija Java 21 en ADR del contexto
  maestro; no se testea contra múltiples JDK en paralelo.
- **Self-hosted runners:** descartados por presupuesto $0 (un equipo
  gratis en GitHub Actions usa runners hosted). Re-evaluable si el
  proyecto crece y los minutos de CI se vuelven cuello de botella.
- **Cobertura de tests (coverage):** sin caso concreto que lo motive
  todavía, se aplaza sin ADR. Revisitar cuando aparezca un módulo
  crítico que justifique la inversión en tooling.
- **Aislamiento Surefire/Failsafe:** fuera de alcance, criterio de
  revisita en ADR-009, Decisión 8.
- **Smoke runtime del contenedor en CI:** `docker build` valida que el
  `Dockerfile` compila, no que el contenedor arranca y responde
  (`/actuator/health` UP, SIGTERM limpio → graceful shutdown). El smoke
  manual del paso 8 de la Rama 1 verificó el runtime una vez a mano.
  Reproducirlo en CI requiere levantar el contenedor con datasource
  accesible — vía service container (contradice ADR-009, Decisión 6:
  paridad local↔CI estricta, sin `services:`) o como smoke parcial
  sin DB (no cubre el wiring real). Aplazar a Fase 3 / cuando exista
  CD con target de despliegue concreto, donde el smoke runtime es
  pieza natural del pipeline de CD, no del de CI puro que ADR-009
  acota.
- **Dependabot** para alertas de dependencias desactualizadas / CVEs:
  nativo de GitHub, gratuito en repositorios públicos, abre PRs
  automáticos con un `dependabot.yml` mínimo. Aplazado por la misma
  regla de timing de ADRs del proyecto que se aplicó para
  Surefire/Failsafe (ADR-009, Decisión 8): sin caso real que lo
  motive todavía (sin CVEs, sin módulo crítico que lo justifique).
  Revisitar cuando aparezca el primer CVE real o un módulo crítico
  que justifique la inversión en tooling de alertas. Que quede
  constancia de que se consideró, no de que se olvidó.

## 12. Trazabilidad

- [ADR-009 — Estrategia de CI y Git Hooks](../adr/ADR-009-estrategia-de-ci-y-git-hooks.md):
  Decisiones con trade-offs (este documento es su materialización
  operativa).
- [ADR-004 — Estándares de Desarrollo y Gobernanza](../adr/ADR-004-estandares-de-desarrollo-y-gobernanza.md):
  Fija Checkstyle + Spotless (Java) y ESLint + Prettier (JS) como
  herramientas; delegó la integración concreta en el pipeline a este
  documento.
- [ADR-008 — Testcontainers para testing de integración](../adr/ADR-008-testcontainers-para-testing-de-integracion.md):
  Fija Testcontainers + `@ServiceConnection`; delegó el "Docker-in-Docker
  en CI a vigilar" y el "gate de calidad en CI" a este documento.