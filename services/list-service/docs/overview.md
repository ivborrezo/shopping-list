# Overview de list-service

> Mapa de estado del servicio `list-service`: qué hace, qué expone y qué
> tiene pendiente. Este fichero enlaza a los documentos de detalle en lugar
> de repetirlos; no es un volcado.

## Qué hace

Servicio placeholder de gestión de listas de la compra y sus ítems. Todavía
no hay implementación: solo existe su contrato de API, definido Design-First
en [`api-contract.yaml`](./api-contract.yaml), que servirá de base para la
implementación en Fase 1.

## Endpoints implementados

Ninguno aún; el contrato de diseño está en
[`api-contract.yaml`](./api-contract.yaml). El contrato define, sin estar
operativos todavía, la gestión de listas (`/lists` y `/lists/{id}`, incluido el
renombrado mediante `PATCH`) y de sus ítems (`/lists/{id}/items` y
`/lists/{id}/items/{itemId}`), junto con los eventos de dominio
`list.created`, `list.renamed`, `list.deleted`, `list.item.added`,
`list.item.removed` y `list.item.purchased`.

> El detalle del contrato (parámetros, respuestas, errores) vive en
> [`api-contract.yaml`](./api-contract.yaml); aquí solo se indica qué hay
> implementado.

## Tablas y migraciones

No aplica todavía: el servicio no tiene implementación ni
`database-schema.md`. El esquema se documentará en ese fichero cuando exista.

## Dependencias

- _Infraestructura:_ PostgreSQL prevista (base de datos `list-db`), bajo el
  patrón database-per-service ([ADR-002](../../../docs/adr/ADR-002-database-per-service-pattern.md)).
- _Servicios:_ consumirá datos de `product-service` por API en el futuro.

## Deudas abiertas

Ninguna: el servicio está sin implementar.

## Fuera de alcance

- **Autorización de lectura (Fase 4).** Las lecturas (`GET /lists/{id}`) no
  comprueban propiedad: cualquiera que conozca el identificador puede leer una
  lista. Es un hueco aceptado de forma consciente durante las Fases 1-3, mientras
  el propietario es un placeholder sin identidad real
  ([ADR-006](../../../docs/adr/ADR-006-identificacion-propietario-sin-autenticacion.md));
  se cierra en Fase 4 con autorización por membresía.
- **Colaboración y membresía (Fase 4).** La compartición de listas entre
  colaboradores queda fuera de Fase 1, sin campos reservados en el contrato; se
  añadirá de forma aditiva en Fase 4 con el modelo real de identidad. Se registra
  como alcance diferido en el [ROADMAP](../../../ROADMAP.md).

## Reglas de uso

- **`overview.md` es un mapa, no un volcado**: enlaza a los documentos de
  detalle en lugar de repetirlos. Si el detalle cambia, se actualiza en su
  documento de origen, no aquí.
- **Es la única fuente de verdad del estado del servicio**: el README raíz
  solo enlaza a este fichero; cualquier discrepancia se resuelve aquí.