<div align="center">

# 🛒 ShoppingList

**Sistema de lista de la compra colaborativa construido como arquitectura de microservicios Cloud-Native.**

Java 21 · Spring Boot 4.x · PostgreSQL · Docker

</div>

[![CI](https://github.com/iv-borrezo/shopping-list/actions/workflows/product-service.yml/badge.svg)](https://github.com/iv-borrezo/shopping-list/actions/workflows/product-service.yml)

---

## Índice

- [Descripción del sistema](#descripción-del-sistema)
- [Arquitectura](#arquitectura)
- [Estructura del repositorio](#estructura-del-repositorio)
- [Inicio rápido](#inicio-rápido)
- [Documentación](#documentación)
  - [Diagramas de arquitectura](#diagramas-de-arquitectura)
  - [Arquitectura de eventos y convenciones](#arquitectura-de-eventos-y-convenciones)
  - [Contratos de API](#contratos-de-api-diseño-design-first)
  - [Guías de desarrollo](#guías-de-desarrollo)
  - [Estrategia de CI/CD](#estrategia-de-cicd)
  - [Architecture Decision Records (ADR)](#architecture-decision-records-adr)
- [Roadmap](#roadmap)
- [Autor](#autor)

---

## Descripción del sistema

ShoppingList permite crear y gestionar listas de la compra de forma
colaborativa. El sistema está diseñado siguiendo principios
**Cloud-Native**: cada dominio de negocio es un microservicio
independiente con su propia base de datos, y toda la infraestructura
local está definida como código mediante Docker Compose.

> **Estado actual:** Fase 1 (MVP Core) en desarrollo. `docker compose up`
> levanta `product-db`, `list-db` y `product-service` (Spring Boot,
> build multi-stage desde `Dockerfile`). `product-service` expone CRUD
> completo de `/categories` (con paginación) y `/base-products`
> con soporte multiidioma (es/en/eu), búsqueda textual y filtros, y de
> `/user-products` con snapshot copy-on-create desde un producto base.
> También gestiona favoritos y recientes de usuario: `POST
> /user-products/{id}/favorite` como toggle, `GET /user-products/favorites`
> paginado con nombres resueltos y `GET /user-products/recents` con el top
> 10 más reciente.
> CI en verde (Testcontainers + failsafe), git hooks y 13 ADRs
> documentando las decisiones de arquitectura. `list-service` es un
> placeholder con su contrato de API definido (Design-First).

---

## Arquitectura

Cada microservicio tiene su propia base de datos PostgreSQL,
físicamente separada (instancia propia, no un esquema compartido), y
ningún servicio accede directamente a la base de datos de otro — toda
relación entre dominios se resolverá a través de la API correspondiente.
Esta decisión, sus consecuencias y las alternativas descartadas están
documentadas en
[ADR-002 — Database-per-Service Pattern](./docs/adr/ADR-002-database-per-service-pattern.md).

| Servicio | Contenedor | Puerto local |
|---|---|---|
| `product-service` | `shopping-list-product-service` | `8081` |
| `product-db` | `shopping-list-product-db` (PostgreSQL) | `5434` |
| `list-db` | `shopping-list-list-db` (PostgreSQL) | `5435` |

---

## Estructura del repositorio

```
shopping-list/
├── docker-compose.yml
├── .env.example
├── .github/workflows/       # CI pipeline (product-service)
├── githooks/                # pre-commit + commit-msg
├── config/checkstyle/       # Google Java Style (compartido)
├── docs/
│   ├── adr/                 # 13 ADRs
│   ├── architecture/        # C4 Level 2
│   ├── cicd/                # Estrategia CI/CD
│   ├── contributing/        # Guías de contribución
│   └── events/
├── product-service/         # Spring Boot + PostgreSQL
│   ├── Dockerfile (multi-stage)
│   ├── src/
│   └── docs/
│       ├── api-contract.yaml
│       └── local-setup.md
└── list-service/            # Placeholder (contrato de API)
    └── docs/
        └── api-contract.yaml
```

`product-service` ya es operacionalmente independiente: build Maven,
`Dockerfile` multi-stage, tests con Testcontainers y pipeline de CI
propio. `list-service` es un placeholder con su contrato de API
definido; su implementación replicará la misma estructura.

---

## Inicio rápido

### Requisitos previos

- [Docker](https://docs.docker.com/get-docker/) >= 24.x
- [Docker Compose](https://docs.docker.com/compose/) >= 2.x

### Levantar el entorno local

```bash
# 1. Clonar el repositorio
git clone https://github.com/iv-borrezo/shopping-list.git
cd shopping-list

# 2. Preparar variables de entorno
cp .env.example .env

# 3. Levantar el sistema
docker compose up -d
```

Para el proceso completo (activación de hooks, verificación, setup por
servicio), consulta el [Setup completo](./docs/contributing/setup.md).

---

## Documentación

### Diagramas de arquitectura

| Documento | Descripción |
|---|---|
| [C4 Nivel 2 — Diagrama de Contenedores](./docs/architecture/c4-level2-containers.md) | Vista de contenedores del sistema completo (arquitectura objetivo por fases, codificada por color según estado de implementación) |

### Arquitectura de eventos y convenciones

| Documento | Descripción |
|---|---|
| [Arquitectura de eventos](./docs/events/event-architecture.md) | Qué eventos existen, quién los publica y quién los consumirá, y por qué. Documento provisional, pendiente de la elección de message broker |
| [Convenciones de logging](./docs/contributing/logging.md) | Criterio MDC vs. structured key-value, formato de salida por perfil y ciclo de vida del `correlationId`. Documento provisional hasta Fase 6 |

### Contratos de API (diseño, Design-First)

Redactados antes de implementar ningún controller, como contrato de
diseño previo al código. Una vez exista la implementación, se marcarán
como snapshot histórico de diseño.

| Servicio | Contrato |
|---|---|
| `product-service` | [OpenAPI](./product-service/docs/api-contract.yaml) |
| `list-service` | [OpenAPI](./list-service/docs/api-contract.yaml) |

### Guías de desarrollo

| Documento | Descripción |
|---|---|
| [Contribuir a ShoppingList](./CONTRIBUTING.md) | Índice de guías de contribución: setup completo, commits, ramas, estilo de código, testing, logging y entorno local |
| [Setup local de `product-service`](./product-service/docs/local-setup.md) | Prerrequisitos, variables de entorno, escenarios de ejecución (CLI, VSCode, Docker Compose), tests y troubleshooting |
| [Esquema de BD de `product-service`](./product-service/docs/database-schema.md) | Tablas `category`, `category_translation`, `base_product`, `base_product_translation`, `user_product`, `user_favorite_product` y `user_recent_product` con migraciones Flyway |

### Estrategia de CI/CD

| Documento | Descripción |
|---|---|
| [Estrategia de CI/CD del monorepo](./docs/cicd/cicd-strategy.md) | Documento operativo derivado de ADR-009 |

### Architecture Decision Records (ADR)

Las decisiones de arquitectura del proyecto se documentan como
Architecture Decision Records (ADR) en [`docs/adr/`](./docs/adr/).

| ADR | Decisión | Estado |
|---|---|---|
| [ADR-001 — Monorepo vs. Polyrepo](./docs/adr/ADR-001-monorepo-vs-polyrepo.md) | Estrategia de repositorio único para todo el sistema | ✅ Redactado |
| [ADR-002 — Database-per-Service Pattern](./docs/adr/ADR-002-database-per-service-pattern.md) | Base de datos independiente y físicamente aislada por servicio | ✅ Redactado |
| [ADR-003 — Service Discovery nativo y descarte de Eureka](./docs/adr/ADR-003-service-discovery-nativo-y-descarte-de-eureka.md) | DNS interno (Docker Compose / AWS Cloud Map) en lugar de un Service Registry como Netflix Eureka | ✅ Redactado |
| [ADR-004 — Estándares de Desarrollo y Gobernanza](./docs/adr/ADR-004-estandares-de-desarrollo-y-gobernanza.md) | Documentación de arquitectura (Markdown; Mermaid no se usa por el momento), Conventional Commits y Checkstyle (Google Java Style) | ✅ Redactado |
| [ADR-005 — Convención de nombrado de eventos](./docs/adr/ADR-005-convencion-nombrado-eventos.md) | `<aggregate>.<past_tense_action>` en snake_case, agnóstica al lenguaje (proyecto políglota) | ✅ Redactado |
| [ADR-006 — Identificación de propietario sin autenticación](./docs/adr/ADR-006-identificacion-propietario-sin-autenticacion.md) | `ownerId` como placeholder en el body (Fase 1-3), validado solo en formato, sin registro compartido entre servicios ni plan de migración a Fase 4 | ✅ Redactado |
| [ADR-007 — Flyway como herramienta de migraciones](./docs/adr/ADR-007-flyway-como-herramienta-de-migraciones.md) | Flyway (edición Community) como gestor único del esquema de cada base de datos por servicio; `ddl-auto: none`, migraciones versionadas en SQL | ✅ Redactado |
| [ADR-008 — Testcontainers para testing de integración](./docs/adr/ADR-008-testcontainers-para-testing-de-integracion.md) | Tests de integración contra PostgreSQL real vía contenedores efímeros; descarte de H2 por discrepancias de comportamiento | ✅ Redactado |
| [ADR-009 — Estrategia de CI y Git Hooks](./docs/adr/ADR-009-estrategia-de-ci-y-git-hooks.md) | Estrategia de CI (sin CD) en dos capas (pre-commit local + GitHub Actions remoto) para el monorepo políglota: hook nativo vía `core.hooksPath` con tres capas y dispatch por servicio, gate híbrido advisory→hard, Testcontainers en `ubuntu-latest` hosted con paridad local↔CI, caching Maven + BuildKit `type=gha`. Cierra delegaciones pendientes de ADR-004 y ADR-008 | ✅ Aceptado |
| [ADR-010 — Política de Testing (TDD vs. test-after)](./docs/adr/ADR-010-politica-de-testing-tdd-vs-test-after.md) | TDD como política por defecto; tests unitarios (Mockito) para lógica aislada e integración (Testcontainers) para flujos E2E, con excepciones legítimas documentadas | ✅ Redactado |
| [ADR-011 — Estrategia de internacionalización y fallback](./docs/adr/ADR-011-estrategia-de-internacionalizacion-y-fallback.md) | Resolución de `Accept-Language` vía `AcceptHeaderLocaleResolver`, fallback exacto → inglés → primer disponible, resolución del nombre en capa de servicio (no JPQL), validación de `locale` en dos capas (Bean Validation + servicio) | ✅ Redactado |
| [ADR-012 — Modelo de productos base vs usuario](./docs/adr/ADR-012-modelo-productos-base-vs-usuario.md) | Dos agregados/tablas separados (`base_product` con i18n Table, `user_product` texto libre monolingüe). `based_on_base_id` como snapshot copy-on-create + trazabilidad inmutable. PK externa compuesta `(productId, productType)`. Flags de compartición inertes hasta Fase 4. Detalle de favoritos/recientes diferido a ADR-013 | ✅ Redactado |
| [ADR-013 — Favoritos y recientes (detalle de implementación)](./docs/adr/ADR-013-favoritos-y-recientes-detalle-de-implementacion.md) | PK compuesta (user_id, product_id, product_type) sin FK física (validación en capa de aplicación, precedente de list_item en list-service), last_used_at como criterio de ordenación de recientes, disparador de recientes solo en el toggle de favorito en Fases 1-3, sin endpoint de touch explícito | ✅ Redactado |

Los ADRs se redactan a medida que se toman decisiones de arquitectura
en cada fase; la tabla refleja el estado actual de las mismas.

---

## Roadmap

- [x] **Fase 1** — MVP Core: `product-service` (categories + base-products + user-products + favoritos y recientes), [ ] `list-service`
- [ ] **Fase 2** — `frontend` (React)
- [ ] **Fase 3** — `api-gateway` + `config-service`
- [ ] **Fase 4** — Seguridad: `auth-service` (Keycloak + OAuth2/OIDC)
- [ ] **Fase 5** — Comunicación asíncrona: `notification-service`
- [ ] **Fase 6** — Observabilidad: OpenTelemetry + Prometheus + Grafana + Loki

---

## Autor

**iv.borrezo** — [iv.borrezo@gmail.com](mailto:iv.borrezo@gmail.com)
