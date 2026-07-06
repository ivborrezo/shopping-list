# Convenciones de Logging

> **Estado:** documento provisional (Fase 1-5). Cubre únicamente
> convenciones de código relativas a logging: qué información se loguea,
> cómo se estructura y cómo se correlaciona entre servicios. No cubre
> distributed tracing, métricas ni dashboards — eso llega en Fase 6.
>
> **Fase 6 (Observabilidad):** este documento se moverá (`git mv`, para
> conservar el historial) a `docs/observability/` y se ampliará con la
> integración de OpenTelemetry / Micrometer Tracing. En ese momento,
> `traceId`/`spanId` gestionados por el stack de tracing sustituirán
> (o convivirán con) el `correlationId` artesanal descrito aquí.

## Objetivo

Definir cómo se logueia en todos los microservicios del proyecto para que:

1. Los logs sean legibles durante el desarrollo local.
2. Los logs ya estén estructurados de forma compatible con Loki/Grafana,
   de modo que en Fase 6 baste con añadir un consumidor (Promtail) sin
   modificar código de negocio ya escrito.
3. Sea posible reconstruir el recorrido completo de una petición a través
   de varios servicios.

## Idioma y niveles

- Todos los logs internos se escriben **en inglés**, independientemente
  del `locale` de la petición, para mantener consistencia en
  observabilidad.
- Niveles:
  - `ERROR`: algo rompió el flujo y requiere atención.
  - `WARN`: situación anómala pero recuperable (ej. fallback de
    traducción a inglés cuando no existe la entrada solicitada).
  - `INFO`: eventos de negocio relevantes (lista creada, producto
    añadido). Son los candidatos naturales a dashboards en Fase 6.
  - `DEBUG`: detalle técnico, desactivado por defecto salvo depuración
    puntual.

## Formato de salida por entorno (Spring Profiles)

Se loguea siempre a **stdout** (nunca a fichero), pero con un formato
distinto según el perfil activo. Esto es coherente con el despliegue
objetivo del proyecto (Docker Compose en local, AWS ECS Fargate en
producción): el sistema de ficheros del contenedor es efímero, así que
escribir logs a disco no aporta nada y, si conviviera con la salida por
consola, duplicaría innecesariamente el I/O de la aplicación. La
plataforma (driver de logs de Docker, agente de CloudWatch en Fargate,
o Promtail leyendo stdout en Fase 6) ya se encarga de capturar y
enrutar stdout — no hace falta gestionar rotación de ficheros ni
volúmenes propios.

- **Perfil `local`** (el del día a día en Docker Compose): texto plano
  legible para humanos, con el `correlationId` visible entre corchetes.
- **Cualquier otro perfil** (o el que se use al integrar Promtail en
  Fase 6, o el despliegue en Fargate): JSON estructurado completo por
  stdout, vía `logstash-logback-encoder`.

```xml
<!-- logback-spring.xml -->

<!-- Entorno local: consola legible para humanos -->
<springProfile name="local">
  <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
    <encoder>
      <pattern>%d{HH:mm:ss} %-5level [%X{correlationId}] %logger{36} - %msg%n</pattern>
    </encoder>
  </appender>
  <root level="INFO">
    <appender-ref ref="CONSOLE"/>
  </root>
</springProfile>

<!-- Cualquier otro entorno: JSON estructurado por stdout -->
<springProfile name="!local">
  <appender name="CONSOLE_JSON" class="ch.qos.logback.core.ConsoleAppender">
    <encoder class="net.logstash.logback.encoder.LogstashEncoder"/>
  </appender>
  <root level="INFO">
    <appender-ref ref="CONSOLE_JSON"/>
  </root>
</springProfile>
```

Ejemplo de la misma línea en cada perfil:

Perfil `local`:
```
10:15:23 INFO  [abc-123] ListService - Adding item to list
```

Perfil no-local (JSON por stdout):
```json
{"@timestamp":"2026-07-06T10:15:23.123Z","level":"INFO","logger_name":"com.shoppinglist.ListService","message":"Adding item to list","correlationId":"abc-123"}
```

Esto evita elegir entre "legible en desarrollo" y "listo para Loki en
Fase 6": el perfil `local` da comodidad inmediata en el día a día, y el
resto de perfiles ya emiten exactamente el formato que Promtail/Loki
esperarán, sin tocar código de negocio cuando llegue esa fase.

## MDC vs. Structured Key-Value (KV): criterio de uso

Se usan ambos mecanismos, cada uno para un tipo de dato distinto. La
pregunta que decide cuál usar es: **¿este campo describe el contexto de
toda la petición/hilo, o el contenido puntual de este evento de
negocio?**

| Tipo de dato | Mecanismo | Ejemplos |
|---|---|---|
| Contexto ambiental de toda la petición | **MDC** | `correlationId`, `userId` (Fase 4), `locale` |
| Contenido puntual de un evento de negocio | **KV (structured arguments)** | `listId`, `productId` |

- **MDC** se establece una vez (típicamente en un filtro HTTP) y
  aparece automáticamente en todas las líneas de log de esa petición,
  sin que el código de negocio tenga que declararlo en cada
  `log.info(...)`.
- **KV** se declara explícitamente en cada línea donde el dato es
  relevante, mediante la API fluida de SLF4J 2.x:

```java
// MDC: se establece una vez, en el filtro de entrada
MDC.put("correlationId", correlationId);
log.info("Adding item to list"); // ya lleva correlationId automáticamente

// KV: explícito en la línea que lo necesita
log.atInfo()
   .addKeyValue("listId", listId)
   .addKeyValue("productId", productId)
   .log("Item added to list");
```

### Condición de validez de MDC

Esta convención asume que el procesamiento de una petición ocurre en
**un único hilo, de entrada a salida** (modelo síncrono actual del
proyecto, incluyendo un futuro uso de virtual threads como sustituto
directo de Tomcat). MDC es thread-local: si en el futuro se introduce
**concurrencia estructurada** dentro de una misma petición (hilos hijos
paralelos) o un **modelo reactivo** (WebFlux), MDC deja de propagarse
correctamente y esta estrategia debe revisarse (alternativas: copiar el
mapa de MDC manualmente a los hilos hijos, `ScopedValue`, o el puente
`context-propagation` de Reactor/Micrometer para WebFlux). No se
espera necesidad de este cambio a corto/medio plazo.

## correlationId: qué es y cómo se gestiona

### Qué es

Un identificador (UUID) que representa **una única petición HTTP
concreta**, de entrada a salida — no una sesión de usuario ni un
usuario. Cada petición nueva genera un `correlationId` distinto, incluso
si proviene del mismo usuario o de la misma sesión de navegador. Su
propósito es permitir aislar, entre logs de múltiples servicios y
múltiples peticiones concurrentes, el recorrido completo de una
petición específica.

No debe confundirse con `userId`: ambos pueden coexistir en MDC y
resuelven preguntas distintas (aislar una petición puntual, frente a
filtrar todo lo que hizo un usuario a través de múltiples peticiones).

### Quién lo genera

No existe una cabecera HTTP estándar para esto: es una convención propia,
implementada mediante un filtro en cada servicio:

```java
@Component
public class CorrelationIdFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String correlationId = Optional.ofNullable(req.getHeader("X-Correlation-Id"))
                .orElse(UUID.randomUUID().toString());
        MDC.put("correlationId", correlationId);
        res.setHeader("X-Correlation-Id", correlationId);
        try {
            chain.doFilter(req, res);
        } finally {
            // MDC.clear() en vez de MDC.remove("correlationId"): borra TODO
            // el mapa del hilo, no solo esta clave. Necesario porque Tomcat
            // reutiliza hilos del pool entre peticiones (Thread Pooling); si
            // en el futuro se añaden más claves al MDC (userId, locale...) y
            // algún punto del código no las limpia explícitamente, un
            // remove() parcial dejaría datos "fantasma" de una petición
            // filtrándose a la siguiente que caiga en ese mismo hilo.
            MDC.clear();
        }
    }
}
```

- Si la petición entrante ya trae la cabecera `X-Correlation-Id`, se
  reutiliza (caso de llamadas internas entre servicios).
- Si no la trae, se genera de cero (caso de una petición nueva desde el
  cliente).

**Nota sobre orden de filtros:** `MDC.clear()` es seguro siempre que
`CorrelationIdFilter` sea el filtro más externo de la cadena (el
primero en entrar, el último en salir). Si en el futuro se añade otro
filtro que también escriba en MDC y se registra *antes* de este, su
contexto podría borrarse prematuramente. Al añadir nuevos filtros que
usen MDC, revisar el orden explícito (`@Order` o el registro en
`FilterRegistrationBean`).

**Fase 1-2 (actual):** cada servicio actúa como su propio punto de
entrada y genera su `correlationId` si no lo recibe.

**Fase 3 (API Gateway):** el Gateway se convierte en el punto de
entrada real del sistema y será quien, en la práctica, genere el
`correlationId` casi siempre. El filtro de cada servicio no cambia: sigue
haciendo exactamente lo mismo (reutilizar si viene, generar si no).

### Propagación en llamadas internas (REST)

Cuando un servicio llama a otro por HTTP (ej. `list-service` →
`product-service`), el `correlationId` debe propagarse como cabecera
saliente. En vez de añadirla manualmente en cada llamada (propenso a
olvidos humanos: alguien escribe una nueva llamada REST y no se acuerda
de incluir la cabecera), se centraliza en un único
`ClientHttpRequestInterceptor`, configurado una sola vez al construir
el `RestClient`:

```java
@Bean
public RestClient restClient() {
    return RestClient.builder()
        .requestInterceptor((request, body, execution) -> {
            String correlationId = MDC.get("correlationId");
            if (correlationId != null) {
                request.getHeaders().add("X-Correlation-Id", correlationId);
            }
            return execution.execute(request, body);
        })
        .build();
}
```

Con esto, cualquier llamada saliente que use este `RestClient` propaga
el `correlationId` automáticamente, sin que el código de negocio tenga
que acordarse en cada punto de llamada (principio DRY, mismo
razonamiento por el que `correlationId` vive en MDC y no se repite
manualmente en cada línea de log).

**Importante:** si por algún motivo la petición saliente no lleva la
cabecera (por ejemplo, una llamada hecha con un cliente HTTP distinto
que no pase por este interceptor), no hay error funcional visible — el
servicio destino simplemente generará un `correlationId` nuevo, propio.
El fallo es silencioso: se pierde la capacidad de correlacionar esa
llamada concreta entre ambos servicios en los logs. Todo cliente HTTP
usado para llamadas internas entre servicios debe pasar por este
interceptor.

### Propagación en eventos (Kafka/RabbitMQ)

Cuando una petición da lugar a la publicación de un evento, el
`correlationId` de origen debe incluirse como header del mensaje, para
permitir correlación causal con los consumidores (ej.
`notification-service` en Fase 5), aunque el consumo ocurra de forma
asíncrona y en un hilo/momento distinto al de la petición original. El
contrato exacto de qué campos lleva cada evento (incluido este) se
documenta en `docs/events/event-architecture.md` y en el
`event-contracts.md` de cada servicio productor — este documento cubre
el concepto y su origen, no el contrato de mensaje en sí.

## Consulta de logs en local (Fase 1-5, sin Loki todavía)

El formato de consola depende del perfil activo (ver sección de
formato de salida), así que el método de filtrado cambia según cuál
esté activo:

```bash
# Perfil "local" (texto plano, el que se usa en el día a día):
# el correlationId aparece entre corchetes, basta un grep.
docker compose logs -f list-service | grep "abc-123"

# Cualquier otro perfil activo (JSON estructurado por stdout):
docker compose logs -f list-service | jq 'select(.correlationId == "abc-123")'
```

En Fase 6, el equivalente en Grafana/Loki será una consulta LogQL sobre
el campo indexado `correlationId`, sin cambios en cómo se genera el dato.
