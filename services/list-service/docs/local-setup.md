# `list-service` — Setup local

Guía operativa para levantar y probar `list-service` en una máquina
recién clonada.

---

## Prerrequisitos

- **Java 21**
- **Docker** >= 24.x
- **Docker Compose** >= 2.x
- **curl** (verificaciones)
- **jq** (opcional; captura de identificadores en el smoke e2e)

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
conectando a PostgreSQL en `localhost:5435`. Este escenario es el modo
"servicios a mano": se llama al servicio directamente en `8082`, sin el
gateway (`8080`).

**Verificación:**

```bash
curl -s http://localhost:8082/actuator/health
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
      "name": "Debug ListServiceApplication",
      "request": "launch",
      "mainClass": "dev.ivborrezo.shoppinglist.list.service.ListServiceApplication",
      "projectName": "list-service",
      "envFile": "${workspaceFolder}/.env"
    }
  ]
}
```

Este fichero se versiona (no contiene secrets). La propiedad `envFile`
carga las variables `LIST_DB_*` del `.env` raíz en la sesión de debug.
Si `envFile` no resuelve rutas relativas en tu sistema, usa
`"env"` explícito:

```json
"env": {
  "LIST_DB_HOST": "<valor de tu .env>",
  "LIST_DB_PORT_OUT": "<valor de tu .env>",
  "LIST_DB_NAME": "<valor de tu .env>",
  "LIST_DB_USER": "<valor de tu .env>",
  "LIST_DB_PASSWORD": "<valor de tu .env>"
}
```

### Arranque

1. Levanta la BD: `docker compose up -d list-db`
2. En VSCode: **Run and Debug** → selecciona
   **Debug ListServiceApplication** → `F5`.
3. El servicio conecta a `localhost:5435` y arranca en `localhost:8082`.
   Log esperado en Debug Console: `Started ListServiceApplication`.

---

## Escenario C — Sistema completo vía Docker Compose

```bash
docker compose up -d
```

Esto levanta los cinco contenedores del sistema: `shopping-list-product-db`
(PostgreSQL), `shopping-list-product-service`, `shopping-list-list-db`
(PostgreSQL), `shopping-list-list-service` y `shopping-list-api-gateway`.
Toda la API se consume por `http://localhost:8080`; los servicios de
dominio no publican su puerto HTTP en el host.

El arranque está ordenado por salud: las bases de datos, después los dos
servicios y por último el gateway (`depends_on: condition: service_healthy`).
El smoke e2e necesita el sistema completo en marcha.

`list-service` construye su imagen desde el `Dockerfile` local y arranca con
el perfil `docker`, conectando a PostgreSQL en `list-db:5432` (DNS interno de
la red Docker). Flyway aplica las migraciones **V1** (`list`) y **V2**
(`list_item`) contra esa base al arrancar.

Tras cambios en el código, `docker compose up -d` no recompila la imagen por
sí solo. Usa `docker compose up -d --build list-service` para forzar el
rebuild.

**Verificación:**

```bash
docker compose ps                          # 5 contenedores healthy/Up
docker compose logs list-service
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

Los escenarios A y B arrancan `list-service` en el host con el perfil
`local`. En ese modo el servicio sigue escuchando en
`http://localhost:8082` y se llama directamente, sin pasar por el gateway
(que no está arrancado). Es entorno de desarrollo, no el sistema
desplegado: no esperes que `8082` sea accesible cuando el sistema está
levantado con Compose.

---

## Ejecución de tests de integración

```bash
./services/list-service/mvnw -f services/list-service/pom.xml clean verify
```

`clean verify` ejecuta los tests de integración con **Testcontainers**, que
levanta un contenedor PostgreSQL efímero — no depende de H2 ni de
ningún paso previo (ni `.env`, ni `list-db` corriendo en Docker Compose). Es
el mismo flujo que ejecuta el pipeline de CI.

---

## Costura `list-service → product-service`

Al añadir un ítem a una lista, `list-service` llama por REST a
`product-service` para resolver el nombre del producto y lo guarda como
snapshot `display_name` en el ítem (ver
[ADR-LS-001](./adr/ADR-LS-001-snapshot-display-name-list-item.md)). La
integración es de solo lectura: `list-service` nunca escribe en el dominio de
productos.

- **Endpoint consultado:** `GET /base-products/{productId}` para
  `productType=BASE` y `GET /user-products/{productId}` para
  `productType=USER`.
- **URL base:** `http://localhost:8081` en el modo "servicios a mano"
  (perfil `local`); `http://product-service:8081` con el perfil `docker`
  (DNS interno de Compose). En el sistema desplegado ese puerto no se
  publica en el host.
- **Timeouts:** 1s de conexión y 2s de lectura.
- **Cabeceras propagadas:** `Accept-Language` y `X-Correlation-Id` de la
  petición entrante, tal cual (lista blanca explícita; no se reenvían el
  resto de cabeceras).
- **Errores traducidos:** un `productId` inexistente o sin nombre responde
  `400 INVALID_PRODUCT_REFERENCE`; una caída o indisponibilidad de
  `product-service` responde `503 PRODUCT_SERVICE_UNAVAILABLE`.
- **Snapshot monolingüe:** el nombre se congela en el idioma de la petición
  del alta y no se re-traduce después.

---

## Smoke e2e manual

Recorre la costura completa: crear una lista, añadir un ítem con el nombre
resuelto por `product-service` y leerlo ya persistido. No se automatiza en
CI; el procedimiento lo reproduce el autor y su promoción a CI queda para
cuando exista CD.

### Procedimiento paso a paso

Vía reproducible con `curl`; el `jq` indicado solo sirve para capturar
identificadores (también puedes copiarlos a mano de la respuesta).

**Prerrequisito.** Stack arriba y gateway sano:

```bash
docker compose up -d
curl -s http://localhost:8080/actuator/health
```

**1. Crear la lista y capturar su `id`.**

La respuesta es `201 Created` con la lista creada; `jq` extrae el `id` y lo
guarda en la variable `LIST_ID`:

```bash
LIST_ID=$(curl -s -X POST http://localhost:8080/lists \
  -H 'Content-Type: application/json' \
  -d '{"ownerId":"3f2504e0-4f89-41d3-9a0c-0305e82c3301","name":"Compra de la semana"}' \
  | jq -r .id)
echo "$LIST_ID"
```

Sin `jq`, ejecuta el mismo `curl` sin la tubería y copia a mano el `id` de la
respuesta; no hace falta crear la lista dos veces.

**2. Añadir un ítem.**

```bash
curl -s -X POST "http://localhost:8080/lists/${LIST_ID}/items?ownerId=3f2504e0-4f89-41d3-9a0c-0305e82c3301" \
  -H 'Content-Type: application/json' \
  -H 'Accept-Language: es' \
  -d '{"productId":"5b597bf7-446e-463e-a2f9-839c360a01cc","productType":"BASE"}'
```

`5b597bf7-446e-463e-a2f9-839c360a01cc` es el producto base `whole_milk`
sembrado por `product-service`. Respuesta esperada `201 Created`:

```json
{
  "id": "<uuid>",
  "productId": "5b597bf7-446e-463e-a2f9-839c360a01cc",
  "productType": "BASE",
  "displayName": "Leche entera",
  "purchased": false
}
```

`displayName` es el nombre resuelto por `product-service` en el idioma de
`Accept-Language`; con `es` devuelve `Leche entera`. El `id` es un UUID
generado en runtime: el valor concreto es ilustrativo.

**(Opcional) 3. Leer la lista con el ítem embebido.**

```bash
curl -s http://localhost:8080/lists/${LIST_ID}
```

El ítem aparece con su `displayName` ya persistido como snapshot, sin volver
a llamar a `product-service`.

---

## Troubleshooting

| Síntoma | Causa / Solución |
|---|---|
| `.env: No such file or directory` al hacer `source .env` | No has copiado la plantilla. Ejecuta `cp .env.example .env`. |
| `Could not connect to localhost:5435` | `list-db` no está corriendo. Ejecuta `docker compose up -d list-db`. |
| `POST /lists/{id}/items` responde `503 PRODUCT_SERVICE_UNAVAILABLE` | `product-service` no está levantado. Arráncalo con `docker compose up -d product-service`. |
| `list-service` no resuelve `product-service` en Compose | El DNS interno falla. Revisa que ambos contenedores estén en la red `shopping-list-net` y que sus `depends_on`/healthchecks estén satisfechos (`docker compose ps`). |
| `POST /lists/{id}/items` responde `400 INVALID_PRODUCT_REFERENCE` | El `productId` no existe o no tiene nombre en `product-service`. Verifica que es un UUID de producto existente y que `productType` coincide (`BASE`/`USER`). |
| `port is already allocated` (8080, 5434 o 5435) | Otro proceso ocupa el puerto publicado. Identifícalo con `lsof -i :8080`, `lsof -i :5434` o `lsof -i :5435`. El `8081`/`8082` solo entra en juego en el modo "servicios a mano". |
| `unknown shorthand flag: 'a' in -a` al hacer `set -a` | Usas una shell no compatible. Usa el `export` alternativo del Escenario A. |
| `curl http://localhost:8082` falla con Compose levantado | Esperado: `list-service` no publica su puerto HTTP. Usa `http://localhost:8080`. |
| El health del gateway no responde `UP` | Revisa los logs en orden: `docker compose logs list-db list-service` y después `docker compose logs api-gateway`. El gateway no arranca hasta que los dos servicios están sanos. |
