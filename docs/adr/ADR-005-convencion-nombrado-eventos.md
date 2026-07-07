# ADR-005: Convención de nombrado de eventos

## Estado

Aceptado.

## Contexto

`list-service` publica eventos de dominio desde Fase 1 (`docs/events/event-architecture.md`
define el catálogo inicial: `list.created`, `list.deleted`, `list.item.added`,
`list.item.removed`, `list.item.purchased`). Antes de escribir la primera
clase de evento en código, es necesario fijar una convención de nombrado
para el campo `eventType` de cada mensaje.

Esta decisión debe tomarse antes de implementar la publicación de eventos:
cambiarla más adelante implicaría modificar el nombre en el productor y en
cualquier consumidor que ya exista (comparadores de `switch`/`if` sobre el
valor de `eventType`, tests, documentación), además de cualquier dato ya
persistido o en tránsito que referencie el nombre antiguo.

Al ser ShoppingList un sistema políglota (Java/Spring Boot en
`product-service` y `list-service`, Node.js en `notification-service` desde
Fase 5), la convención elegida no debe favorecer culturalmente a ningún
lenguaje de programación concreto.

### Alternativas consideradas

1. **PascalCase plano** (`ListCreated`, `ProductAddedToList`): legible, pero
   se lee como convención nativa de clases en Java/C#, no neutral en un
   proyecto políglota. Además, sin separador jerárquico, agrupar eventos
   relacionados (ej. todo lo referente a ítems de lista) requiere parsear el
   string.
2. **kebab-case** (`list-created`, `list-item-added`): el guión es idiomático
   de URLs/slugs, no de tipos de evento; además introduce ambigüedad en
   contextos de parsing donde el guión puede confundirse con un signo menos
   o tener doble uso (flags de CLI, algunos DSLs de configuración).
3. **`<bounded_context>.<aggregate>.<past_tense_action>`** (ej.
   `shopping_list.list.created`): prefijar con el bounded context resuelve
   colisiones de nombre cuando varios equipos, cada uno dueño de su propio
   contexto, publican eventos de forma independiente y podrían nombrar
   igual dos conceptos distintos (práctica habitual en DDD estratégico a
   escala de organización grande). Se descarta por ahora: el proyecto tiene
   un único desarrollador y un único bounded context de facto (la propia
   gestión de listas de la compra), por lo que el riesgo de colisión que
   este prefijo mitiga no aplica todavía. Añadirlo supondría un coste de
   verbosidad (`shopping_list.list.created` frente a `list.created`) sin
   beneficio real en el contexto actual.
4. **`<aggregate>.<past_tense_action>`** con snake_case para palabras
   compuestas dentro de un mismo segmento (`list.created`,
   `list.item.purchase_reverted`): patrón dominante en la industria para
   nombrar tipos de evento (Stripe: `customer.subscription.deleted`,
   `payment_intent.succeeded`; el mismo criterio siguen GitHub, Segment y
   Shopify; alineado con el enfoque de CloudEvents). Jerárquico por diseño
   (el punto separa aggregate de action, y permite sub-recursos como
   `list.item`), agnóstico al lenguaje de programación, y el guión bajo no
   genera la ambigüedad de parsing del guión medio.

## Decisión

Se adopta el patrón **`<aggregate>.<past_tense_action>`**:

- El **aggregate** es el recurso de dominio afectado, en minúsculas, con
  jerarquía de sub-recurso mediante puntos cuando aplica (`list` vs.
  `list.item`).
- La **action** es un verbo en pasado, representando un hecho ya ocurrido
  (Domain Event), no una orden. Si la acción está compuesta por varias
  palabras, se separan con snake_case (ej. `purchase_reverted`, no
  `purchase-reverted`).
- Todo en minúsculas.
- Sin prefijo de bounded context (ver alternativa 3 descartada arriba).
- Este nombre es el valor del campo `eventType` dentro del payload del
  mensaje. Es independiente del nombre físico del canal de transporte
  (topic de Kafka o exchange/cola de RabbitMQ), que se decide en
  `ADR-XXF1-eleccion-message-broker` y puede agrupar varios `eventType` en
  un mismo canal físico.

Ejemplos ya adoptados en el catálogo de `list-service`:

```
list.created
list.deleted
list.item.added
list.item.removed
list.item.purchased
```

## Consecuencias

- Todo evento de dominio nuevo, en cualquier servicio, debe seguir este
  patrón; se revisa en code review.
- La convención es estable frente a la elección de broker (`ADR-XXF1-eleccion-message-broker`)
  y frente al canal físico de transporte: cambiar de Kafka a RabbitMQ (o
  viceversa) no obliga a renombrar ningún `eventType`.
- `docs/events/event-architecture.md` ya adoptaba este patrón de facto
  antes de este ADR; este documento formaliza y justifica esa elección
  como decisión de arquitectura, sin cambiar ningún nombre ya acordado.
- **Trade-off de mapeo políglota (Java vs. Node.js):** un `eventType` con
  puntos (`list.item.added`) no mapea de forma nativa y automática a un
  `enum` de Java vía Jackson por reflexión pura — la convención habitual de
  un enum Java (`LIST_ITEM_ADDED`) no admite puntos ni guiones bajos como
  parte de un identificador jerárquico de ese tipo. Esto obliga a un
  `@JsonProperty` explícito por constante del enum, o a un
  serializador/deserializador custom (`@JsonSerialize`/`@JsonDeserialize`,
  o un módulo de Jackson a medida), en vez de depender de la
  correspondencia automática nombre-de-constante ↔ string. En Node.js el
  coste es menor, al no existir la misma restricción de enums nativos
  (se maneja habitualmente como string plano o un objeto de constantes).
  Se acepta este coste adicional en el lado Java como consecuencia
  consciente de priorizar un `eventType` legible, jerárquico y agnóstico al
  lenguaje por encima de la comodidad de la serialización reflexiva nativa.
- Queda pendiente de definir, fuera del alcance de este ADR, la
  correspondencia exacta entre `eventType` y canal físico (topic/exchange),
  que se resolverá junto con `ADR-XXF1-eleccion-message-broker`.
- Si el proyecto evoluciona hacia múltiples equipos o dominios claramente
  separados que empiecen a compartir nombres de aggregate de forma
  ambigua, revisar la introducción de un prefijo de bounded context
  (alternativa 3 descartada arriba).
