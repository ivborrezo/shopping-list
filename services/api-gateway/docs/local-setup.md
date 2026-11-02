# `api-gateway` — Setup local

Guía operativa para levantar y probar el `api-gateway` en una máquina
recién clonada.

---

## Prerrequisitos

- **Java 21**
- **Docker** >= 24.x
- **Docker Compose** >= 2.x
- **curl** (verificaciones)

Maven se usa vía el wrapper incluido (`./mvnw`), sin instalación aparte.

El gateway no tiene base de datos ni variables de entorno propias: su
configuración se resuelve por perfil en los ficheros de la aplicación. Las
variables `PRODUCT_DB_*` y `LIST_DB_*` pertenecen a los servicios de dominio y
a sus contenedores.

---

## Configuración por perfil

| Perfil | Cuándo se usa | Destinos |
|---|---|---|
| `local` | Arranque con el wrapper en el host (activo por defecto) | `product-service` en `localhost:8081` y `list-service` en `localhost:8082` |
| `docker` | Imagen de contenedor en Compose | `product-service:8081` y `list-service:8082` por DNS interno |

El perfil `local` publica también el endpoint de rutas de Actuator
(`/actuator/gateway/routes`) en modo lectura. Ambos perfiles exponen
`/actuator/health`; ninguno depende de un servidor de configuración
centralizado.

---

## Escenario A — Gateway en el host con servicios en el host (CLI)

El gateway necesita los dos servicios de dominio levantados para enrutar. En
este escenario se arrancan en el host con su perfil `local`, tal como se
describe en sus guías:

```bash
# 1. Bases de datos
docker compose up -d product-db list-db

# 2. En cada terminal, cargar variables de entorno
set -a; source .env; set +a

# 3. En terminales separadas, los dos servicios y el gateway
./services/product-service/mvnw -f services/product-service/pom.xml spring-boot:run
./services/list-service/mvnw -f services/list-service/pom.xml spring-boot:run
./services/api-gateway/mvnw -f services/api-gateway/pom.xml spring-boot:run
```

Cada terminal que arranque un servicio de dominio debe cargar el `.env` antes
de lanzar Maven. El gateway no lo necesita.

El gateway arranca en `http://localhost:8080` y enruta hacia
`http://localhost:8081` y `http://localhost:8082`. Este escenario es el modo
"servicios a mano": se llama al borde en `8080` mientras los servicios siguen
escuchando en sus puertos de desarrollo.

**Verificación:**

```bash
curl -s http://localhost:8080/actuator/health
curl -s http://localhost:8080/categories | head -c 200
curl -s "http://localhost:8080/lists?ownerId=3f2504e0-4f89-41d3-9a0c-0305e82c3301"
```

**Parar:**

`Ctrl+C` para cada proceso Maven. Las bases de datos se paran con
`docker compose down`.

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
      "name": "Debug ApiGatewayApplication",
      "request": "launch",
      "mainClass": "dev.ivborrezo.shoppinglist.gateway.ApiGatewayApplication",
      "projectName": "api-gateway"
    }
  ]
}
```

Este fichero es local y no se versiona (`.vscode/` está en `.gitignore`). El
gateway no necesita cargar variables de entorno para arrancar; si quieres
arrancar también los servicios en el mismo workspace, cada uno tiene su propia
configuración.

### Arranque

1. Levanta las bases de datos y los dos servicios como en el escenario A.
2. En VSCode: **Run and Debug** → selecciona
   **Debug ApiGatewayApplication** → `F5`.
3. El gateway arranca en `localhost:8080`. Log esperado en Debug Console:
   `Started ApiGatewayApplication`.

---

## Escenario C — Sistema completo vía Docker Compose

```bash
cp .env.example .env   # solo la primera vez
docker compose up -d
```

Esto levanta los cinco contenedores del sistema: las dos bases de datos, los
dos servicios de dominio y el gateway. La imagen del gateway fija el perfil
`docker`, resuelve los servicios por DNS interno y publica únicamente el
`8080`.

El arranque está ordenado por salud: las bases de datos, después los dos
servicios y por último el gateway (`depends_on: condition: service_healthy`).
Los tres servicios exponen `/actuator/health` para ese healthcheck.

Tras cambios en el código, `docker compose up -d` no recompila la imagen por
sí solo. Usa `docker compose up -d --build api-gateway` para forzar el
rebuild.

**Verificación:**

```bash
docker compose ps                          # 5 contenedores healthy/Up
curl -s http://localhost:8080/actuator/health
curl -s http://localhost:8080/categories | head -c 200
```

**Bases de datos en loopback.** `product-db` escucha en el host solo en
`127.0.0.1:5434` y `list-db` en `127.0.0.1:5435`. Es una decisión operativa,
no un incidente: permite conectar un cliente de PostgreSQL desde la propia
máquina para depurar sin exponer las bases de datos en la red. No cambies ese
binding a `0.0.0.0`.

**Parar y limpiar volúmenes:**

```bash
docker compose down -v
```

### Verificación del despliegue compuesto

El script [`verify-compose.sh`](../../../scripts/verify-compose.sh) levanta el
sistema, recorre una lectura y una escritura por servicio a través del `8080`
y comprueba que solo el gateway publica un puerto HTTP y que las bases de datos
quedan en loopback. Necesita `docker`, `curl` y `sed` en el `PATH`:

```bash
./scripts/verify-compose.sh
```

Deja el sistema arriba al terminar para poder inspeccionar los logs. Con
`KEEP=0` lo baja al final. El detalle de la topología y las políticas del borde
vive en el [`overview.md`](./overview.md).

---

## Ejecución de tests

```bash
./services/api-gateway/mvnw -f services/api-gateway/pom.xml clean verify
```

La suite del gateway usa WireMock para simular los servicios de dominio y no
necesita ni `docker compose` ni una base de datos en marcha. Es el mismo flujo
que ejecuta el pipeline de CI.

---

## Troubleshooting

| Síntoma | Causa / Solución |
|---|---|
| `port is already allocated` en `8080` | Otro proceso ocupa el puerto del borde. Identifícalo con `lsof -i :8080`. |
| `/categories` o `/lists` responde `503 SERVICE_UNAVAILABLE` | Los servicios de dominio no están levantados o no son alcanzables en el destino del perfil. Revisa `docker compose ps` o que el arranque en el host esté en `8081`/`8082`. |
| Una ruta existente responde `404 ROUTE_NOT_FOUND` | La ruta no está declarada para ningún servicio o el prefijo no coincide. En el perfil `local` puedes inspeccionar el enrutado en `http://localhost:8080/actuator/gateway/routes`. |
| Las peticiones empiezan a responder `429 RATE_LIMIT_EXCEEDED` | El cubo de la IP se ha agotado. Espera el valor de `Retry-After` o reinicia el gateway; el cupo es por proceso y no se comparte. |
| El navegador bloquea la petición por CORS | El origen no está en la whitelist exacta del borde. Añádelo en la configuración CORS si es un origen de desarrollo previsto; los patrones no se aceptan. |
| Una petición con cuerpo grande responde `413` | El `Content-Length` declarado supera el tope del borde. Reduce el cuerpo o revisa el límite configurado. |
| El health del gateway no responde `UP` | Revisa los logs en orden: `docker compose logs product-db product-service` y después `docker compose logs api-gateway`. El gateway no arranca hasta que los dos servicios están sanos. |
| `docker compose port api-gateway 8080` devuelve `invalid IP:0` | El gateway no está levantado o no tiene el puerto publicado. Ejecuta `docker compose up -d` y revisa `docker compose ps`. |
| `curl http://localhost:8081` falla con Compose levantado | Esperado: `product-service` no publica su puerto HTTP. Usa `http://localhost:8080`. |
