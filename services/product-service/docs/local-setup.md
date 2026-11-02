# `product-service` — Setup local

Guía operativa para levantar y probar `product-service` en una máquina
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
`PRODUCT_DB_*`. Fuentes, en orden de preferencia:

1. **`.env`** (raíz del monorepo) — valor real, gitignored.
2. **`.env.example`** — plantilla pública, commiteada.

El password de `.env.example` es un placeholder público;
el de `.env` es un valor privado distinto. El perfil `local` funciona con
ambos.

Variables que consume `product-service`:

| Variable | Descripción |
|---|---|
| `PRODUCT_DB_HOST` | Host de PostgreSQL |
| `PRODUCT_DB_PORT_OUT` | Puerto expuesto al host |
| `PRODUCT_DB_NAME` | Nombre de la base de datos |
| `PRODUCT_DB_USER` | Usuario |
| `PRODUCT_DB_PASSWORD` | Contraseña |

Valores de referencia en `.env.example` (raíz del monorepo); valores
reales en `.env` (gitignored).

---

## Escenario A — BD en Docker + servicio en el host (CLI)

```bash
# 1. BD
docker compose up -d product-db

# 2. Cargar variables de entorno
set -a; source .env; set +a       # bash/zsh
# o bien: export $(grep -v '^#' .env | xargs)

# 3. Arrancar el servicio
./services/product-service/mvnw -f services/product-service/pom.xml spring-boot:run
```

El servicio arranca en `http://localhost:8081`, conectando a PostgreSQL en
`localhost:5434`. Este escenario es el modo "servicios a mano": se llama al
servicio directamente en `8081`, sin el gateway (`8080`).

**Verificación:**

```bash
curl -s http://localhost:8081/actuator/health
curl -s http://localhost:8081/categories | head -c 200
```

**Parar:**

`Ctrl+C` para el proceso Maven. La BD se para con `docker compose down`.

---

## Escenario B — Ejecución en modo debug desde VSCode

Extensión requerida: **Extension Pack for Java** (redhat) o
**Spring Boot Extension Pack** (VMware).

### Configuración de `.vscode/launch.json`

```json
{
  "version": "0.2.0",
  "configurations": [
    {
      "type": "java",
      "name": "Debug ProductServiceApplication",
      "request": "launch",
      "mainClass": "dev.ivborrezo.shoppinglist.product.service.ProductServiceApplication",
      "projectName": "product-service",
      "envFile": "${workspaceFolder}/.env"
    }
  ]
}
```

Este fichero se versiona (no contiene secrets). La propiedad `envFile`
carga las variables `PRODUCT_DB_*` del `.env` raíz en la sesión de debug.
Si `envFile` no resuelve rutas relativas en tu sistema, usa
`"env"` explícito:

```json
"env": {
  "PRODUCT_DB_HOST": "<valor de tu .env>",
  "PRODUCT_DB_PORT_OUT": "<valor de tu .env>",
  "PRODUCT_DB_NAME": "<valor de tu .env>",
  "PRODUCT_DB_USER": "<valor de tu .env>",
  "PRODUCT_DB_PASSWORD": "<valor de tu .env>"
}
```

### Arranque

1. Levanta la BD: `docker compose up -d product-db`
2. En VSCode: **Run and Debug** → selecciona
   **Debug ProductServiceApplication** → `F5`.
3. El servicio conecta a `localhost:5434` y arranca en `localhost:8081`.
   Log esperado en Debug Console: `Started ProductServiceApplication`.

---

## Escenario C — Sistema completo vía Docker Compose

```bash
docker compose up -d
```

Esto levanta los cinco contenedores del sistema: las dos bases de datos
(`shopping-list-product-db` y `shopping-list-list-db`), los dos servicios
(`shopping-list-product-service` y `shopping-list-list-service`) y el
`shopping-list-api-gateway`. Toda la API se consume por
`http://localhost:8080`; los servicios de dominio no publican su puerto
HTTP en el host.

`product-service` construye su imagen desde el `Dockerfile` local (build
lento la primera vez por descarga de dependencias Maven; builds posteriores
reutilizan la cache de BuildKit). Flyway aplica las migraciones contra
`product-db:5432` (DNS interno de la red Docker) al arrancar.

El arranque está ordenado por salud: las bases de datos, después los dos
servicios y por último el gateway (`depends_on: condition: service_healthy`).
Los tres servicios exponen `/actuator/health` para ese healthcheck.

Tras cambios en el código, `docker compose up -d` no recompila la imagen
por sí solo. Usa `docker compose up -d --build product-service` para
forzar el rebuild.

**Verificación:**

```bash
docker compose ps                          # 5 contenedores healthy/Up
docker compose logs product-service | grep "Successfully applied"  # Flyway OK
curl -s http://localhost:8080/actuator/health
curl -s http://localhost:8080/categories | head -c 200
```

**Bases de datos en loopback.** `product-db` escucha en el host solo en
`127.0.0.1:5434` y `list-db` en `127.0.0.1:5435`. Es una decisión
operativa, no un incidente: permite conectar un cliente de PostgreSQL
desde la propia máquina para depurar sin exponer las bases de datos en la
red. No cambies ese binding a `0.0.0.0`.

**Parar y limpiar volúmenes:**

```bash
docker compose down -v
```

### Modo "servicios a mano"

Los escenarios A y B arrancan `product-service` en el host con el perfil
`local`. En ese modo el servicio sigue escuchando en
`http://localhost:8081` y se llama directamente, sin pasar por el gateway
(que no está arrancado). El modo "servicios a mano" es entorno de
desarrollo, no el sistema desplegado: no esperes que `8081` sea accesible
cuando el sistema está levantado con Compose.

---

## Ejecución de tests de integración

```bash
./services/product-service/mvnw -f services/product-service/pom.xml clean verify
```

Testcontainers levanta un contenedor PostgreSQL efímero — no
depende de ningún paso previo (ni `.env`, ni `product-db` corriendo en
Docker Compose). Es el mismo flujo que ejecuta el pipeline de CI.

---

## Troubleshooting

| Síntoma | Causa / Solución |
|---|---|
| `.env: No such file or directory` al hacer `source .env` | No has copiado la plantilla. Ejecuta `cp .env.example .env`. |
| `Mockito is currently self-attaching` (WARN) | Esperado, no bloqueante. Maven añade Mockito como agente Java automático al detectarlo en el classpath de test; el aviso desaparecerá si se configura Mockito como `-javaagent` explícito en `surefire-plugin`. |
| `Could not connect to localhost:5434` | `product-db` no está corriendo. Ejecuta `docker compose up -d product-db`. |
| `Flyway failed to determine applied migrations` | La BD existe pero tiene un estado inconsistente. Haz `docker compose down -v product-db && docker compose up -d product-db` para recrear desde cero. |
| `port is already allocated` (8080, 5434 o 5435) | Otro proceso ocupa el puerto publicado. Identifícalo con `lsof -i :8080`, `lsof -i :5434` o `lsof -i :5435`. El `8081` solo entra en juego en el modo "servicios a mano". |
| `curl http://localhost:8081` falla con Compose levantado | Esperado: `product-service` no publica su puerto HTTP. Usa `http://localhost:8080`. |
| El health del gateway no responde `UP` | Revisa los logs en orden: `docker compose logs product-db product-service` y después `docker compose logs api-gateway`. El gateway no arranca hasta que los dos servicios están sanos. |
| `docker compose port api-gateway 8080` devuelve `invalid IP:0` | El gateway no está levantado o no tiene el puerto publicado. Ejecuta `docker compose up -d` y revisa `docker compose ps`. |
| `unknown shorthand flag: 'a' in -a` al hacer `set -a` | Usas una shell no compatible. Usa el `export` alternativo del Escenario A. |
