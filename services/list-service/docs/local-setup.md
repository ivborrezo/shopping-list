# `list-service` — Setup local

Guía operativa para levantar y probar `list-service` en una máquina
recién clonada.

---

## Prerrequisitos

- **Java 21**
- **Docker** >= 24.x
- **Docker Compose** >= 2.x
- **curl** (verificaciones)

Maven se usa vía el wrapper incluido (`./mvnw`), sin instalación aparte.

---

## Variables de entorno

El servicio resuelve sus datos de conexión mediante variables de entorno
`LIST_DB_*`. Fuentes, en orden de preferencia:

1. **`.env`** (raíz del monorepo) — valor real, gitignored.
2. **`.env.example`** — plantilla pública, commiteada.

El password de `.env.example` es un placeholder público; el de `.env` es un
valor privado. Ambos perfiles (`local` y `docker`) funcionan con cualquiera
de las dos fuentes.

Variables que consume `list-service`:

| Variable | Descripción |
|---|---|
| `LIST_DB_HOST` | Host de PostgreSQL (perfil `local`) |
| `LIST_DB_PORT_OUT` | Puerto de PostgreSQL expuesto al host |
| `LIST_DB_NAME` | Nombre de la base de datos |
| `LIST_DB_USER` | Usuario |
| `LIST_DB_PASSWORD` | Contraseña |

Valores de referencia en `.env.example` (raíz del monorepo); valores reales
en `.env` (gitignored).

El puerto HTTP del servicio es fijo: `8082`. No se configura por variable de
entorno.

---

## Escenario A — BD en Docker + servicio en el host (CLI)

```bash
# 1. BD
docker compose up -d list-db

# 2. Cargar variables de entorno
set -a; source .env; set +a       # bash/zsh
# o bien: export $(grep -v '^#' .env | xargs)

# 3. Arrancar el servicio
./services/list-service/mvnw -f services/list-service/pom.xml spring-boot:run
```

El servicio arranca en `http://localhost:8082` con el perfil `local`,
conectando a PostgreSQL en `localhost:5435`.

**Verificación:**

```bash
curl -s http://localhost:8082/actuator/health
```

**Parar:**

`Ctrl+C` para el proceso Maven. La BD se para con `docker compose down`.

---

## Escenario B — Sistema completo vía Docker Compose

```bash
docker compose up -d
```

Esto levanta todo el stack, incluyendo `shopping-list-list-db` (PostgreSQL)
y `shopping-list-list-service`.

`list-service` construye su imagen desde el `Dockerfile` local y arranca con
el perfil `docker`, conectando a PostgreSQL en `list-db:5432` (DNS interno de
la red Docker). Flyway está configurado y aplicará las migraciones contra esa
base al arrancar, aunque hoy no existe ninguna todavía.

Tras cambios en el código, `docker compose up -d` no recompila la imagen por
sí solo. Usa `docker compose up -d --build list-service` para forzar el
rebuild.

**Verificación:**

```bash
docker compose ps                          # servicios healthy/Up
docker compose logs list-service
curl -s http://localhost:8082/actuator/health
```

**Parar y limpiar volúmenes:**

```bash
docker compose down -v
```

---

## Ejecución de tests

```bash
./services/list-service/mvnw -f services/list-service/pom.xml clean verify
```

El proyecto está configurado para tests de integración con **Testcontainers**,
que levanta un PostgreSQL `postgres:16-alpine` efímero — no depende de H2 ni
de ningún paso previo (ni `.env`, ni `list-db` corriendo en Docker Compose).

En el estado actual del servicio `clean verify` valida el formato
(Spotless/Checkstyle) y la compilación. Cuando se añadan los tests de
integración, la misma orden los ejecutará contra Testcontainers.

---

## Troubleshooting

| Síntoma | Causa / Solución |
|---|---|
| `.env: No such file or directory` al hacer `source .env` | No has copiado la plantilla. Ejecuta `cp .env.example .env`. |
| `Could not connect to localhost:5435` | `list-db` no está corriendo. Ejecuta `docker compose up -d list-db`. |
| `port is already allocated` (8082 o 5435) | Otro proceso ocupa el puerto. Identifícalo con `lsof -i :8082` o `lsof -i :5435`. |
| `unknown shorthand flag: 'a' in -a` al hacer `set -a` | Usas una shell no compatible. Usa el `export` alternativo del Escenario A. |
| El health no responde `UP` | Revisa los logs del contenedor con `docker compose logs list-service`. |
