# Overview de list-service

> Mapa de estado del servicio `list-service`: qué hace, qué expone y qué
> tiene pendiente. Este fichero enlaza a los documentos de detalle en lugar
> de repetirlos; no es un volcado.

## Qué hace

Servicio de gestión de listas de la compra y sus ítems. Expone la API REST de
listas e ítems, persiste sobre la base de datos dedicada `list-db` mediante
migraciones Flyway, resuelve de forma de solo lectura el nombre del producto
contra `product-service` como snapshot `display_name` al añadir un ítem, y
publica los eventos de dominio de listas e ítems. El contrato está definido
Design-First en [`api-contract.yaml`](./api-contract.yaml) e implementado en su
totalidad para Fase 1.

## Endpoints implementados

- `GET /lists` — lista las listas de un propietario, paginadas por `ownerId` y ordenadas por actividad.
- `POST /lists` — crea una lista vacía.
- `GET /lists/{id}` — obtiene el detalle de una lista con sus ítems.
- `PATCH /lists/{id}` — renombra una lista.
- `DELETE /lists/{id}` — elimina una lista y sus ítems en cascada.
- `POST /lists/{id}/items` — añade un producto a la lista resolviendo su nombre como snapshot.
- `PATCH /lists/{id}/items/{itemId}` — marca o desmarca un ítem como comprado.
- `DELETE /lists/{id}/items/{itemId}` — elimina un ítem de la lista.

> El detalle del contrato (parámetros, respuestas, errores) vive en
> [`api-contract.yaml`](./api-contract.yaml); aquí solo se lista qué hay
> implementado.

Los errores siguen un contrato único: RFC 9457 `ProblemDetail`
(`application/problem+json`) con extensión `code` y un catálogo estable de
8 códigos (7 de negocio + `VALIDATION_FAILED`), sin localización de mensajes
en el backend (la localización es del frontend). Ver
[ADR-014](../../../docs/adr/ADR-014-estrategia-de-manejo-de-errores.md).

## Tablas y migraciones

El esquema se define con migraciones Flyway sobre la base de datos dedicada
`list-db` y se documenta en [`database-schema.md`](./database-schema.md), que
es la fuente de detalle (tablas, constraints, relaciones y diagrama ER).

- `list` (V1) — listas de la compra: identidad externa `public_id` (UUID),
  propietario `owner_id`, `name` y marcas de auditoría.
- `list_item` (V2) — ítems de una lista: FK `list_id` a `list` en cascada,
  referencia polimórfica al producto por `(product_type, product_id)` y snapshot
  `display_name`.

## Dependencias

- _Infraestructura:_ PostgreSQL (base de datos `list-db`), bajo el patrón
  database-per-service ([ADR-002](../../../docs/adr/ADR-002-database-per-service-pattern.md)).
- _Servicios:_ `product-service`, consultado por REST en el alta de ítems para
  resolver el nombre del producto como snapshot `display_name`. Integración de
  solo lectura: `list-service` no escribe en el dominio de productos.

## Deudas abiertas

> El catálogo central de deudas técnicas diferidas vive en
> [TECH_DEBT.md](../../../TECH_DEBT.md); aquí se resumen las específicas del
> servicio.

- **Disparador de recientes.** El segundo disparador de recientes
  (`last_used_at`) depende de que `product-service` consuma el evento
  `list.item.added` (Fase 5); hasta entonces el único camino hacia la marca de
  reciente es el toggle de favorito
  ([ADR-013](../../../docs/adr/ADR-013-favoritos-y-recientes-detalle-de-implementacion.md)).
- **`product.deleted`.** El borrado de un `user_product` referenciado desde un
  `list_item` deja el ítem sin resolución de nombre; la deuda se resuelve cuando
  `product-service` publique el evento `product.deleted`
  ([ADR-012](../../../docs/adr/ADR-012-modelo-productos-base-vs-usuario.md)).
- **Resiliencia y transacciones distribuidas.** La integración con
  `product-service` es de solo lectura y sin transacciones distribuidas, así que
  no hay consistencia atómica entre ambos dominios. La deuda sigue latente y su
  resolución (Saga/Outbox) queda fuera de alcance mientras la integración siga
  siendo de solo lectura
  ([ADR-002](../../../docs/adr/ADR-002-database-per-service-pattern.md)).

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
- **`snapshot_locale`.** El snapshot `display_name` es monolingüe: el idioma se
  resuelve al vuelo en `product-service` reenviando `Accept-Language`, y
  `list-service` solo conserva el nombre resultante. Añadir un
  `snapshot_locale` que fije el idioma del snapshot queda fuera de alcance hasta
  que exista el requisito.
- **Outbox/Saga.** La coordinación transaccional distribuida entre servicios
  queda fuera de alcance mientras la integración con `product-service` siga
  siendo de solo lectura; su resolución llegará ligada a la deuda de
  transacciones distribuidas
  ([ADR-002](../../../docs/adr/ADR-002-database-per-service-pattern.md)).

## Reglas de uso

- **`overview.md` es un mapa, no un volcado**: enlaza a los documentos de
  detalle en lugar de repetirlos. Si el detalle cambia, se actualiza en su
  documento de origen, no aquí.
- **Es la única fuente de verdad del estado del servicio**: el README raíz
  solo enlaza a este fichero; cualquier discrepancia se resuelve aquí.
