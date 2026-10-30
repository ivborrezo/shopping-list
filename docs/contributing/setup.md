# Setup completo

Proceso de instalación y arranque del repositorio completo (monorepo).

## Requisitos previos

- [Docker](https://docs.docker.com/get-docker/) >= 24.x
- [Docker Compose](https://docs.docker.com/compose/) >= 2.x

## Clonar el repositorio

```bash
git clone https://github.com/ivborrezo/shopping-list.git
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
| `api-gateway` (API REST pública) | `http://localhost:8080` |
| `product-service` (interno) | `http://product-service:8081` (red Docker) |
| `list-service` (interno) | `http://list-service:8082` (red Docker) |
| `product-db` (PostgreSQL, loopback) | `127.0.0.1:5434` |
| `list-db` (PostgreSQL, loopback) | `127.0.0.1:5435` |

La API se consume siempre por `http://localhost:8080`. `product-service` y
`list-service` no publican su puerto HTTP en el host; solo son accesibles
desde la red interna de Compose. Las bases de datos se enlazan
explícitamente a `127.0.0.1` para depuración local.

## Verificación

```bash
curl -s http://localhost:8080/actuator/health
curl -s http://localhost:8080/categories | head -c 200
curl -s "http://localhost:8080/lists?ownerId=3f2504e0-4f89-41d3-9a0c-0305e82c3301"
```

## Parar y limpiar

```bash
docker compose down -v
```

## Setup por servicio

Cada microservicio documenta su propio entorno de desarrollo aislado:

- [Setup local de product-service](../../services/product-service/docs/local-setup.md)
- [Setup local de list-service](../../services/list-service/docs/local-setup.md)

Referencias: [Convenciones del entorno local](local-environment.md) ·
[Estrategia de CI/CD](../cicd/cicd-strategy.md).
