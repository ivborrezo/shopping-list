# Setup completo

Proceso de instalación y arranque del repositorio completo (monorepo).

## Requisitos previos

- [Docker](https://docs.docker.com/get-docker/) >= 24.x
- [Docker Compose](https://docs.docker.com/compose/) >= 2.x

## Clonar el repositorio

```bash
git clone https://github.com/iv-borrezo/shopping-list.git
cd shopping-list
```

## Variables de entorno

```bash
cp .env.example .env
```

## Activar los hooks de git

```bash
git config core.hooksPath githooks
```

Los hooks validan el formato de los commits y ejecutan saneos básicos en
cada commit (ver [Estrategia de CI/CD](../cicd/cicd-strategy.md)).

## Arrancar el sistema

```bash
docker compose up -d
```

Una vez arrancado:

| Servicio | URL local |
|---|---|
| `product-service` (API REST) | `http://localhost:8081` |
| `product-db` (PostgreSQL) | `localhost:5434` |
| `list-db` (PostgreSQL) | `localhost:5435` |

## Verificación

```bash
curl -s http://localhost:8081/actuator/health
curl -s http://localhost:8081/categories | head -c 200
```

## Parar y limpiar

```bash
docker compose down -v
```

## Setup por servicio

Cada microservicio documenta su propio entorno de desarrollo aislado:

- [Setup local de product-service](../../product-service/docs/local-setup.md)

Referencias: [Convenciones del entorno local](local-environment.md) ·
[Estrategia de CI/CD](../cicd/cicd-strategy.md).
