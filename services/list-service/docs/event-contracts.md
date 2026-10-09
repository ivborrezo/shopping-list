# Contrato de eventos de `list-service`

> Contrato de **productor**: el schema exacto de los eventos de dominio que
> publica `list-service`. La convención de nombrado, los campos obligatorios
> de todo evento y la vista de relaciones productor-consumidor viven en
> [`../../../docs/events/event-architecture.md`](../../../docs/events/event-architecture.md);
> los endpoints que disparan cada evento, en [`./api-contract.yaml`](./api-contract.yaml).
> Este documento no repite esos contenidos: fija únicamente el formato de los
> mensajes que `list-service` emite al operar sobre listas.

## Envelope

Todo evento viaja dentro del mismo envelope, serializado en **camelCase**:

| Campo | Tipo | Descripción |
|---|---|---|
| `eventType` | string | Tipo del evento según la convención `<aggregate>.<past_tense_action>` ([ADR-005](../../../docs/adr/ADR-005-convencion-nombrado-eventos.md)). |
| `correlationId` | string (UUID) | Identificador de correlación propagado desde la cabecera `X-Correlation-Id` de la petición que originó el hecho. |
| `occurredAt` | string (date-time, ISO-8601 UTC) | Instante en que ocurrió el hecho, capturado en el momento de la acción y no en el de la publicación. |
| `payload` | object | Carga específica de cada evento; su schema se detalla evento por evento. |

Ejemplo de mensaje completo:

```json
{
  "eventType": "list.created",
  "correlationId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "occurredAt": "2026-03-14T10:15:30.123Z",
  "payload": {
    "listId": "0195c9f3-7a2b-7c4d-8e1f-2a3b4c5d6e7f",
    "ownerId": "3f2504e0-4f89-41d3-9a0c-0305e82c3301",
    "name": "Compra de la semana"
  }
}
```

> **Identificadores.** Todos los ids del `payload` son externos: `public_id`
> en formato UUID ([ADR-016](../../../docs/adr/ADR-016-estrategia-de-identificadores-publicos.md)).
> El id interno de base de datos nunca cruza este contrato.

## Eventos publicados

Los ejemplos de cada evento muestran el objeto `payload`; el envelope que lo
transporta es siempre el descrito arriba.

### `list.created`

Disparado por `POST /lists`.

| Campo | Tipo | Descripción |
|---|---|---|
| `listId` | string (UUID) | Identificador público de la lista creada. |
| `ownerId` | string (UUID) | Identificador del propietario de la lista. |
| `name` | string | Nombre con el que se creó la lista. |

```json
{
  "listId": "0195c9f3-7a2b-7c4d-8e1f-2a3b4c5d6e7f",
  "ownerId": "3f2504e0-4f89-41d3-9a0c-0305e82c3301",
  "name": "Compra de la semana"
}
```

### `list.renamed`

Disparado por `PATCH /lists/{id}`.

| Campo | Tipo | Descripción |
|---|---|---|
| `listId` | string (UUID) | Identificador público de la lista renombrada. |
| `ownerId` | string (UUID) | Identificador del propietario de la lista. |
| `oldName` | string | Nombre que tenía la lista antes del renombrado. |
| `newName` | string | Nombre con el que quedó la lista tras el renombrado. |

```json
{
  "listId": "0195c9f3-7a2b-7c4d-8e1f-2a3b4c5d6e7f",
  "ownerId": "3f2504e0-4f89-41d3-9a0c-0305e82c3301",
  "oldName": "Compra de la semana",
  "newName": "Compra del finde"
}
```

### `list.deleted`

Disparado por `DELETE /lists/{id}`.

| Campo | Tipo | Descripción |
|---|---|---|
| `listId` | string (UUID) | Identificador público de la lista eliminada. |
| `ownerId` | string (UUID) | Identificador del propietario de la lista. |
| `name` | string | Nombre que tenía la lista en el momento del borrado. |

```json
{
  "listId": "0195c9f3-7a2b-7c4d-8e1f-2a3b4c5d6e7f",
  "ownerId": "3f2504e0-4f89-41d3-9a0c-0305e82c3301",
  "name": "Compra del finde"
}
```

### `list.item.added`

Disparado por `POST /lists/{id}/items`.

| Campo | Tipo | Descripción |
|---|---|---|
| `listId` | string (UUID) | Identificador público de la lista a la que se añade el ítem. |
| `itemId` | string (UUID) | Identificador público del ítem creado. |
| `productType` | string | Tipo del producto referenciado (`BASE` o `USER`). |
| `productId` | string (UUID) | Identificador público del producto referenciado. |
| `displayName` | string | Snapshot del nombre del producto en el momento del alta. |

```json
{
  "listId": "0195c9f3-7a2b-7c4d-8e1f-2a3b4c5d6e7f",
  "itemId": "0195c9f3-9c4e-7a1b-8f2c-3d4e5f6a7b8c",
  "productType": "BASE",
  "productId": "3f2504e0-4f89-41d3-9a0c-0305e82c3301",
  "displayName": "Leche entera"
}
```

### `list.item.removed`

Disparado por `DELETE /lists/{id}/items/{itemId}`.

| Campo | Tipo | Descripción |
|---|---|---|
| `listId` | string (UUID) | Identificador público de la lista de la que se elimina el ítem. |
| `itemId` | string (UUID) | Identificador público del ítem eliminado. |
| `productType` | string | Tipo del producto referenciado (`BASE` o `USER`). |
| `productId` | string (UUID) | Identificador público del producto referenciado. |
| `displayName` | string | Snapshot del nombre que tenía el producto al eliminarse. |

```json
{
  "listId": "0195c9f3-7a2b-7c4d-8e1f-2a3b4c5d6e7f",
  "itemId": "0195c9f3-9c4e-7a1b-8f2c-3d4e5f6a7b8c",
  "productType": "BASE",
  "productId": "3f2504e0-4f89-41d3-9a0c-0305e82c3301",
  "displayName": "Leche entera"
}
```

### `list.item.purchased`

Disparado por `PATCH /lists/{id}/items/{itemId}`.

| Campo | Tipo | Descripción |
|---|---|---|
| `listId` | string (UUID) | Identificador público de la lista a la que pertenece el ítem. |
| `itemId` | string (UUID) | Identificador público del ítem marcado o desmarcado. |
| `productType` | string | Tipo del producto referenciado (`BASE` o `USER`). |
| `productId` | string (UUID) | Identificador público del producto referenciado. |
| `displayName` | string | Snapshot del nombre del producto almacenado en el ítem. |
| `purchased` | boolean | `true` marca el ítem como comprado; `false` lo desmarca. |

```json
{
  "listId": "0195c9f3-7a2b-7c4d-8e1f-2a3b4c5d6e7f",
  "itemId": "0195c9f3-9c4e-7a1b-8f2c-3d4e5f6a7b8c",
  "productType": "BASE",
  "productId": "3f2504e0-4f89-41d3-9a0c-0305e82c3301",
  "displayName": "Leche entera",
  "purchased": true
}
```
