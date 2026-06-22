<div align="center">

# 🛒 ShoppingList

**Sistema de lista de la compra colaborativa construido como arquitectura de microservicios Cloud-Native.**

Java 21 · Spring Boot 3.x · PostgreSQL · Docker

</div>

---

## Índice

- [Descripción del sistema](#descripción-del-sistema)
- [Arquitectura](#arquitectura)
- [Estructura del repositorio](#estructura-del-repositorio)
- [Inicio rápido](#inicio-rápido)
- [Documentación](#documentación)
- [Roadmap](#roadmap)
- [Autor](#autor)

---

## Descripción del sistema

ShoppingList permite crear y gestionar listas de la compra de forma
colaborativa. El sistema está diseñado siguiendo principios
**Cloud-Native**: cada dominio de negocio es un microservicio
independiente con su propia base de datos, y toda la infraestructura
local está definida como código mediante Docker Compose.

> **Estado actual:** Fase 1 (MVP Core) en desarrollo. Esta sesión cubre
> únicamente infraestructura base — `docker compose up` levanta las
> bases de datos de cada servicio. Todavía no hay lógica de negocio
> implementada.

---

## Arquitectura

Cada microservicio tiene su propia base de datos PostgreSQL,
físicamente separada (instancia propia, no un esquema compartido), y
ningún servicio accede directamente a la base de datos de otro — toda
relación entre dominios se resolverá a través de la API correspondiente.
Esta decisión, sus consecuencias y las alternativas descartadas están
documentadas en
[ADR-002 — Database-per-Service Pattern](./docs/adr/ADR-002-database-per-service-pattern.md).

| Servicio | Base de datos | Puerto local |
|---|---|---|
| `product-service` | `product-db` (PostgreSQL) | `5434` |
| `list-service` | `list-db` (PostgreSQL) | `5435` |

---

## Estructura del repositorio

```
shopping-list/
├── docker-compose.yml
├── .env.example
├── .gitignore
├── README.md
├── docs/
│   └── adr/
├── product-service/
└── list-service/
```

Cada microservicio es operacionalmente independiente dentro del
monorepo: tendrá su propio sistema de build, su propio `Dockerfile` y
sus propios tests, una vez se implemente en próximas sesiones.

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

# 3. Levantar las bases de datos
docker compose up
```

Una vez arrancado:

| Servicio | URL local |
|---|---|
| `product-db` (PostgreSQL) | `localhost:5434` |
| `list-db` (PostgreSQL) | `localhost:5435` |

Para parar y limpiar los volúmenes de datos:

```bash
docker compose down -v
```

---

## Documentación

Las decisiones de arquitectura del proyecto se documentan como
Architecture Decision Records (ADR) en [`docs/adr/`](./docs/adr/).

| ADR | Decisión | Estado |
|---|---|---|
| [ADR-001 — Monorepo vs. Polyrepo](./docs/adr/ADR-001-monorepo-vs-polyrepo.md) | Estrategia de repositorio único para todo el sistema | ✅ Redactado |
| [ADR-002 — Database-per-Service Pattern](./docs/adr/ADR-002-database-per-service-pattern.md) | Base de datos independiente y físicamente aislada por servicio | ✅ Redactado |
| [ADR-003 — Service Discovery nativo y descarte de Eureka](./docs/adr/ADR-003-service-discovery-nativo-y-descarte-de-eureka.md) | DNS interno (Docker Compose / AWS Cloud Map) en lugar de un Service Registry como Netflix Eureka | ✅ Redactado |

Esta sección crecerá a medida que se tomen nuevas decisiones de
arquitectura en próximas fases.

---

## Roadmap

- [ ] **Fase 1** — MVP Core: `product-service` + `list-service`
- [ ] **Fase 2** — `frontend` (React)
- [ ] **Fase 3** — `api-gateway` + `config-service`
- [ ] **Fase 4** — Seguridad: `auth-service` (Keycloak + OAuth2/OIDC)
- [ ] **Fase 5** — Comunicación asíncrona: `notification-service`
- [ ] **Fase 6** — Observabilidad: OpenTelemetry + Prometheus + Grafana + Loki

---

## Autor

**iv.borrezo** — [iv.borrezo@gmail.com](mailto:iv.borrezo@gmail.com)
