# Arquitectura de Eventos

> **Estado:** documento provisional. Cubre la relación productor-consumidor
> y el catálogo de eventos de dominio, de forma agnóstica al broker
> concreto. La elección de broker (`ADR-XXF1-eleccion-message-broker`)
> sigue pendiente. La convención de nombrado ya está formalizada en
> `ADR-005-convencion-nombrado-eventos` (Aceptado), adoptada de facto en
> este documento desde su primera versión.
> `notification-service`
> (Fase 5) no existe todavía: las entradas de "consumidores" de eventos
> con valor de notificación reflejan el consumidor previsto, no uno
> implementado.

## Qué cubre este documento, y qué no

Este documento es el mapa a nivel de sistema de la comunicación
**asíncrona** entre servicios: qué eventos existen, quién los publica,
quién los consume (o consumirá) y por qué. No sustituye a otros
documentos ya existentes en la convención del proyecto — los
complementa, cada uno a un nivel de detalle distinto:

- **`docs/architecture/c4-level2-containers.md`**: confirma *que* existe
  un canal de comunicación asíncrona entre contenedores (ej. "list-service
  publica eventos de dominio → broker → notification-service los
  consume"), sin desglosar los eventos concretos que viajan por él.
- **Este documento**: desglosa *qué* eventos concretos viajan por ese
  canal, quién publica y consume cada uno, y su propósito de negocio.
  Es la vista de relaciones, no de estructura exacta de datos.
- **`<servicio>/docs/event-contracts.md`**: el schema exacto del
  payload — nombres de campos, tipos, formato — de los eventos que ese
  servicio concreto publica. Es la vista de contrato de **productor**.

### El hueco del contrato de consumidor

La convención actual de `event-contracts.md` cubre únicamente el lado
productor ("servicios que publican eventos"). No existe todavía un
documento equivalente para el lado consumidor. Mientras
`notification-service` no exista (Fase 5), este documento asume ese
papel de forma ligera, listando qué consumidor está previsto para cada
evento y con qué propósito. Cuando `notification-service` se implemente,
se decidirá si esto es suficiente o si merece su propio documento de
contrato de consumidor.

## Naming convention

Se adopta el patrón `<aggregate>.<past-tense-action>`, jerárquico por
puntos, en minúsculas, con snake_case para palabras compuestas dentro
de un mismo segmento (ej. `purchase_reverted`, no
`purchase-reverted`). Es el patrón dominante en la industria para
tipos de evento (Stripe: `customer.subscription.deleted`,
`payment_intent.succeeded`; el mismo criterio siguen GitHub, Segment y
Shopify).

- El **aggregate** es el recurso de dominio afectado, con jerarquía de
  sub-recurso cuando aplica (`list` vs. `list.item`).
- La **action** es un verbo en pasado: representa un hecho ya ocurrido
  (Domain Event), no una orden.
- Es agnóstico al lenguaje de programación (ni PascalCase de Java/C#, ni
  camelCase de JavaScript), coherente con la naturaleza políglota del
  proyecto (Spring Boot + Node.js).
- Este naming es el valor del campo `eventType` dentro del payload del
  mensaje — es independiente del nombre físico del topic/exchange, que
  se decide en `ADR-XXF1-eleccion-message-broker` y puede agrupar
  varios `eventType` en un mismo canal físico.

## Campos obligatorios de todo evento

Todo evento publicado por cualquier servicio debe incluir, además de su
payload específico:

- **`eventType`**: el nombre del evento según la convención anterior
  (ej. `list.created`).
- **`correlationId`**: propagado desde el contexto de la petición HTTP
  que originó el evento, para permitir correlación causal en los logs
  entre la petición de entrada y su(s) efecto(s) asíncrono(s), aunque el
  consumo ocurra en un momento y servicio distintos. Ver
  `docs/logging-conventions.md` para el ciclo de vida completo del
  `correlationId`.

### Por qué el evento no lleva `locale` ni lista de destinatarios

El evento de dominio describe **el hecho** (algo que ya ocurrió), no
las preferencias de quienes van a reaccionar a él. Incluir el `locale`
del actor que disparó la acción sería engañoso en un contexto
colaborativo: notificaría a todos los colaboradores de una lista en el
idioma de quien hizo la acción, no en el suyo propio — y como una
lista puede tener varios colaboradores con `locale` distintos, un único
campo en el evento no puede representar correctamente "el idioma de
cada destinatario" en ningún caso.

Por el mismo motivo, el evento tampoco incluye la lista de
colaboradores de la lista afectada (solo lleva su `listId`).

Ambos datos — `locale` de cada destinatario y quién es cada
destinatario — son responsabilidad de `notification-service` en el
momento de consumo, no del evento en el momento de publicación:

- El `locale` de cada usuario se resolverá contra la fuente de
  identidad (Keycloak, Fase 4), que ya está prevista para soportar los
  tres idiomas del proyecto.
- Los colaboradores de una lista se resolverán consultando la
  información de membresía de la lista (vía API síncrona a
  `list-service`, o un mecanismo a definir cuando exista colaboración
  real en el roadmap).

Ambos puntos quedan pendientes de diseño concreto para Fase 5, cuando
`notification-service` se implemente.

## Catálogo de eventos

### `list-service`

| Evento | Disparado por | Consumidor previsto | Propósito de negocio |
|---|---|---|---|
| `list.created` | `POST /lists` | — (sin consumidor activo) | Se ha creado una nueva lista. |
| `list.deleted` | `DELETE /lists/{id}` | `notification-service` (Fase 5) | Notificar a los colaboradores de la lista que ha sido eliminada. |
| `list.item.added` | `POST /lists/{id}/items` | — (sin consumidor activo) | Se ha añadido un producto a una lista existente. |
| `list.item.removed` | `DELETE /lists/{id}/items/{itemId}` | `notification-service` (Fase 5) | Notificar a los colaboradores de que un producto fue eliminado de la lista. |
| `list.item.purchased` | `PATCH /lists/{id}/items/{itemId}` | `notification-service` (Fase 5) | Notificar a los colaboradores de que un ítem fue marcado como comprado o pendiente (el mismo evento cubre ambos sentidos mediante un campo booleano en el payload, ej. `purchased: true/false`). |

**Nota sobre eventos sin consumidor activo:** `list.created` y
`list.item.added` se publican desde Fase 1 sin que ningún servicio los
consuma todavía. Esto es una decisión consciente, no un descuido: el
desacoplamiento propio de la mensajería asíncrona permite instrumentar
la publicación ahora, evitando tener que modificar el código de
negocio de `list-service` retroactivamente cuando aparezca un
consumidor futuro. `list.deleted` y `list.item.removed` siguen el mismo
razonamiento: se publican desde ya pensando en el valor de notificación
que aportarán en Fase 5.

### `product-service`

`product-service` no publica eventos en esta fase. No se ha identificado
ningún consumidor para cambios en el catálogo de productos o
categorías: el patrón Snapshot aplicado en `list_item.display_name`
(ver Modelo de productos) desacopla deliberadamente a `list-service` de
cambios futuros en el catálogo, eliminando la necesidad de que
`product-service` notifique esos cambios de forma asíncrona.

## Publicación desacoplada del broker: Ports and Adapters

Para poder implementar `list-service` sin esperar a
`ADR-XXF1-eleccion-message-broker`, la publicación de eventos se
diseña detrás de un puerto de salida (patrón **Ports and Adapters /
Arquitectura Hexagonal**), de forma que el dominio invoca una interfaz
sin conocer el broker concreto:

```java
public interface DomainEventPublisher {
    void publish(DomainEvent event);
}
```

Esto permite implementar hoy un adapter provisional (ej.
`LoggingDomainEventPublisher`, que simplemente registra el evento en
el log) y sustituirlo por `KafkaDomainEventPublisher` o
`RabbitDomainEventPublisher` el día que se resuelva la elección de
broker, sin modificar la lógica de negocio que dispara cada evento.

## Pendiente de decisión

Lo siguiente depende de ADRs aún no redactados y queda fuera del
alcance actual de este documento:

- **Canal físico de transporte** (topic de Kafka vs. exchange/cola de
  RabbitMQ), agrupación de eventos por canal, particionado — depende
  de `ADR-XXF1-eleccion-message-broker`.
- **Naming convention**: ya formalizada en `ADR-005-convencion-nombrado-eventos`
  (Aceptado). Este documento adoptaba el criterio de facto desde su primera
  versión; el ADR referencia este catálogo como precedente de uso.
- **Semántica de entrega** (at-least-once, idempotencia en el
  consumidor) — se definirá cuando exista un consumidor real
  (`notification-service`, Fase 5).
- **Resolución de `locale` y destinatarios por evento**: mecanismo
  concreto por el que `notification-service` obtendrá el idioma de
  cada colaborador y la lista de colaboradores de una lista — a
  definir en Fase 5 (ver sección "Por qué el evento no lleva `locale`
  ni lista de destinatarios").
- **Contrato de consumidor**: si el listado ligero de esta primera
  versión es suficiente o si merece un documento propio, a decidir en
  Fase 5.
