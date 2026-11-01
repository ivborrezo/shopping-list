# Overview de api-gateway

> Mapa de estado del componente `api-gateway`: qué hace, qué expone y qué
> tiene pendiente. Este fichero enlaza a los documentos de detalle en lugar
> de repetirlos; no es un volcado.

## Qué hace

API Gateway de ShoppingList: punto único de entrada al sistema y responsable
de las políticas transversales del borde. Enruta las peticiones hacia
`product-service` y `list-service` y concentra la correlación, el contrato de
error, CORS, el rate limiting, el tope de cuerpo y los timeouts, de modo que
los servicios de dominio no tengan que resolverlos por su cuenta. No contiene
lógica de negocio ni accede a datos: su estado propio se limita al contador
efímero del rate limiting. La decisión está registrada en
[ADR-017](../../../docs/adr/ADR-017-api-gateway-propio-frente-a-gateway-gestionado.md).

## Superficie expuesta

El borde no mantiene un contrato propio: la superficie pública es la unión de
los contratos de los servicios de dominio, publicados bajo un único host y
sin exponer la topología interna. Las rutas `/categories/**`,
`/base-products/**` y `/user-products/**` se enrutan a `product-service`; las
rutas `/lists/**`, a `list-service`.

- Contrato de [`product-service`](../../product-service/docs/api-contract.yaml).
- Contrato de [`list-service`](../../list-service/docs/api-contract.yaml).

Los contratos declaran el borde como servidor y describen las respuestas que
el propio gateway puede generar, de forma que un cliente generado desde ellos
entiende tanto un error de negocio como un rechazo del borde.

La superficie de diagnóstico se reduce a `GET /actuator/health`, expuesto en
todos los perfiles para el healthcheck de Compose. El endpoint de rutas de
Actuator (`/actuator/gateway`) solo se publica en el perfil `local` y en modo
lectura, para inspeccionar el enrutado durante el desarrollo.

## Topología y puertos

| Componente | Puerto interno | Publicación en el host |
|---|---|---|
| `api-gateway` | `8080` | `8080` |
| `product-service` | `8081` | Ninguna; solo red interna |
| `list-service` | `8082` | Ninguna; solo red interna |
| `product-db` | `5432` | `127.0.0.1:5434` |
| `list-db` | `5432` | `127.0.0.1:5435` |

El gateway es el único contenedor que publica un puerto HTTP. Los servicios de
dominio se resuelven por su nombre DNS dentro de la red de Compose
(`product-service:8081` y `list-service:8082`) y no son alcanzables desde el
host. Las bases de datos se enlazan explícitamente a loopback para depuración
local.

El arranque del sistema está ordenado por salud: primero las bases de datos,
después los servicios y por último el gateway. Un servicio caído al arrancar
impide que el gateway levante, de modo que un fallo de dependencia se ve en el
primer intento y no se enmascara con reinicios.

En el modo "servicios a mano" los servicios se arrancan en el host en `8081` y
`8082`, y el gateway con el perfil `local` apunta a `localhost`. Es un entorno
de desarrollo: en el sistema desplegado con Compose esos puertos no se
publican.

## Políticas de borde

### Correlación

El gateway normaliza `X-Correlation-Id` en toda petición entrante. Conserva el
valor recibido solo si llega como valor único y es un UUID canónico; en
cualquier otro caso genera uno nuevo. El identificador definitivo viaja en la
petición reenviada, en el contexto reactivo y en el eco de la respuesta, y los
servicios reciben siempre un valor válido sin tener que validarlo. El detalle
del ciclo de vida vive en [`docs/logging.md`](../../../docs/logging.md).

### Contrato de error

Los fallos propios del borde se responden con RFC 9457 `ProblemDetail`
(`application/problem+json`), la extensión `code` y un título estable en
inglés: ruta no encontrada, servicio no disponible, límite de tasa excedido y
error interno. Las respuestas de los servicios de dominio se reenvían sin
reescribir: el borde solo traduce lo que nace en él. Un rechazo por límite de
tasa incluye `Retry-After` con la espera hasta el siguiente token. El
vocabulario sigue el contrato de errores del sistema
([ADR-014](../../../docs/adr/ADR-014-estrategia-de-manejo-de-errores.md)).

### CORS

La política CORS se declara una vez en el borde y cubre todas las rutas: una
whitelist exacta de orígenes de desarrollo del frontend, los métodos de la
API, las cabeceras `Content-Type`, `Accept-Language` y `X-Correlation-Id`, y
la exposición de `X-Correlation-Id` al navegador. No se permiten credenciales.
La misma política se aplica a los errores generados por el propio gateway, no
solo a las respuestas de los servicios. Los patrones de origen no cuentan como
orígenes permitidos: la comparación es exacta.

### Rate limiting

El límite se aplica por IP de socket, con un token bucket en memoria por
proceso y por cliente. Cada petición de API consume un token; el preflight
CORS y la superficie de actuator no consumen cupo. Cuando el cubo se agota, la
respuesta es un `429` con `RATE_LIMIT_EXCEEDED` y `Retry-After`. El borde no
interpreta `X-Forwarded-For`: sin una política de confianza, esa cabecera
haría el límite evitable. La capacidad y la ventana se configuran por perfil.

El estado en memoria no se comparte entre instancias: el cupo efectivo se
multiplica con las réplicas y se pierde al reiniciar. La limitación y su
disparador de reevaluación están registrados en
[`TECH_DEBT.md`](../../../TECH_DEBT.md#rate-limiting-en-memoria-por-instancia).

### Tope de cuerpo y timeouts

El borde rechaza con `413` las peticiones cuyo `Content-Length` supera el tope
configurado, antes de consumir cupo de rate limiting. La comprobación no
bufferiza el cuerpo: se apoya en la longitud declarada, así que los cuerpos sin
`Content-Length` no se limitan en este punto. El transporte hacia los servicios
tiene tiempos de conexión y respuesta acotados y no reintenta automáticamente;
un fallo de conexión o un timeout se traducen a `503 SERVICE_UNAVAILABLE`.

## Configuración por perfil

| Aspecto | `local` | `docker` |
|---|---|---|
| Destino de `product-service` | `http://localhost:8081` | `http://product-service:8081` |
| Destino de `list-service` | `http://localhost:8082` | `http://list-service:8082` |
| Superficie de actuator | `health` y `gateway` (lectura) | `health` |
| Rate limiting | Capacidad de desarrollo | Capacidad del despliegue local |

El perfil `local` es el activo por defecto al arrancar con el wrapper; la
imagen de contenedor fija el perfil `docker`. Ambos perfiles resuelven la
configuración de forma local, sin depender de un servidor de configuración
centralizado. La capacidad y la ventana del rate limiting viven en el fichero
de configuración del borde y sus overrides por perfil; no se duplican aquí.

## Cómo ejecutarlo

La guía operativa, con escenarios de arranque, verificación y resolución de
problemas, vive en [`local-setup.md`](./local-setup.md). Para levantar el
sistema completo basta con Docker Compose; para desarrollar el borde de forma
aislada se arranca con el wrapper Maven y el perfil `local`.

## Deudas abiertas

> El catálogo central de deudas técnicas diferidas vive en
> [TECH_DEBT.md](../../../TECH_DEBT.md); aquí se resumen las específicas del
> componente.

- **Rate limiting en memoria por instancia.** El cupo no se comparte entre
  réplicas y se pierde al reiniciar; la resolución pasa por un store
  compartido y una resolución de IP de confianza cuando el despliegue lo
  requiera.
- **Ventana residual en la purga de cubos.** Una petición concurrente puede
  refrescar un cubo justo después de la revalidación de la purga; la siguiente
  petición lo recrea con la capacidad completa. El impacto está acotado y la
  deuda queda registrada en `TECH_DEBT.md`.

## Fuera de alcance

- **Autenticación y autorización.** El borde no valida tokens ni impone
  identidad: es el punto donde se integrará la validación cuando exista el
  modelo de seguridad. Hoy el propietario sigue siendo el placeholder de los
  servicios.
- **Lógica de dominio y agregación de respuestas.** El gateway no conoce el
  modelo de negocio ni compone respuestas de varios servicios; si la agregación
  llegara a justificarse, su lugar sería un BFF.
- **Reintentos automáticos y circuit breaking.** El borde no reintenta
  operaciones: un fallo de transporte se traduce a `503` y el cliente decide.
- **Rate limiting distribuido y resolución de IP real detrás de un proxy.**
  Se abordan cuando el despliegue tenga más de una réplica o un proxy delante.
- **Puerto de management separado y cota de cuerpos sin `Content-Length`.**
  Decisiones de despliegue, no carencias de lo implementado.
- **Configuración centralizada.** Corresponde al servidor de configuración, no
  al borde.

## Reglas de uso

- **`overview.md` es un mapa, no un volcado**: enlaza a los documentos de
  detalle en lugar de repetirlos. Si el detalle cambia, se actualiza en su
  documento de origen, no aquí.
- **Es la única fuente de verdad del estado del componente**: el README raíz
  solo enlaza a este fichero; cualquier discrepancia se resuelve aquí.
