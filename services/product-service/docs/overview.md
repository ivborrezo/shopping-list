# Overview de product-service

> Mapa de estado del servicio `product-service`: qué hace, qué expone y qué
> tiene pendiente. Este fichero enlaza a los documentos de detalle en lugar
> de repetirlos; no es un volcado.

## Qué hace

Servicio de catálogo y productos de usuario de ShoppingList. Gestiona el
catálogo gestionado por el sistema —categorías y productos base localizados
(es/en/eu)— y el catálogo personal de cada usuario —productos monolingües con
snapshot copy-on-create desde un producto base, favoritos y recientes—.
Spring Boot + PostgreSQL, con base de datos propia siguiendo el patrón
database-per-service ([ADR-002](../../../docs/adr/ADR-002-database-per-service-pattern.md)).

## Endpoints implementados

- `GET /categories` — lista el catálogo de categorías, paginado y traducido según `Accept-Language`.
- `POST /categories` — crea una categoría con sus traducciones.
- `GET /categories/{id}` — obtiene una categoría por id.
- `GET /base-products` — lista el catálogo de productos base, paginado, con nombre y descripción localizados (es/en/eu), búsqueda textual y filtro por categoría.
- `POST /base-products` — crea un producto base (operación administrativa).
- `GET /base-products/{id}` — obtiene un producto base por id.
- `PATCH /base-products/{id}` — edita un producto base (operación administrativa).
- `DELETE /base-products/{id}` — elimina un producto base (operación administrativa).
- `GET /user-products` — lista los productos de un propietario, paginado por `ownerId`.
- `POST /user-products` — crea un producto de usuario; con `basedOnBaseId` copia los valores del producto base (snapshot copy-on-create en el locale de la petición) y lo conserva como trazabilidad inmutable.
- `GET /user-products/{id}` — obtiene un producto de usuario por id.
- `PATCH /user-products/{id}` — edita un producto de usuario (solo su propietario).
- `DELETE /user-products/{id}` — elimina un producto de usuario (solo su propietario).
- `POST /user-products/{id}/favorite` — toggle de favorito: marca o desmarca el producto del propietario.
- `GET /user-products/favorites` — lista los favoritos de un propietario, paginado, con el nombre resuelto según el tipo de producto.
- `GET /user-products/recents` — top 10 de productos recientes por `last_used_at`.

> El detalle del contrato (parámetros, respuestas, errores) vive en
> [`api-contract.yaml`](./api-contract.yaml); aquí solo se lista qué hay
> implementado.

Los errores siguen un contrato único: RFC 9457 `ProblemDetail`
(`application/problem+json`) con extensión `code` y un catálogo estable de
15 códigos (14 de negocio + `VALIDATION_FAILED`), sin localización de
mensajes en el backend (la localización es del frontend). Ver
[ADR-014](../../../docs/adr/ADR-014-estrategia-de-manejo-de-errores.md).

## Tablas y migraciones

7 tablas: `category`, `category_translation`, `base_product`,
`base_product_translation`, `user_product`, `user_favorite_product` y
`user_recent_product`. El esquema se define exclusivamente mediante
migraciones Flyway V1-V11, incluidos los seeds de categorías y productos
base. El esquema detallado, las relaciones y el diagrama ER viven en
[`database-schema.md`](./database-schema.md).

## Dependencias

- Servicios: ninguno en Fase 1.
- Infraestructura: PostgreSQL (`product-db`).

## Deudas abiertas

> El catálogo central de deudas técnicas diferidas vive en
> [TECH_DEBT.md](../../../TECH_DEBT.md); aquí se resumen las específicas del
> servicio.

- Disparador de recientes limitado al toggle de favorito y sin endpoint de
  touch: `last_used_at` solo se actualiza al marcar un favorito; no hay otro
  camino hacia la marca de reciente hasta que `list-service` publique el
  evento "producto añadido a lista". Deuda consciente, documentada en
  [ADR-013](../../../docs/adr/ADR-013-favoritos-y-recientes-detalle-de-implementacion.md)
  (Decisiones 3 y 4).

## Fuera de alcance

- Autenticación real: `ownerId`/`user_id` es un placeholder validado solo en
  formato, sin tabla de usuarios ni enforcement de identidad
  ([ADR-006](../../../docs/adr/ADR-006-identificacion-propietario-sin-autenticacion.md)).
- i18n de UI: la localización de mensajes de la interfaz es responsabilidad
  del frontend, no del backend.
- Mensajes de error localizados en el backend (ver ADR-014).

## Reglas de uso

- **`overview.md` es un mapa, no un volcado**: enlaza a los documentos de
  detalle en lugar de repetirlos. Si el detalle cambia, se actualiza en su
  documento de origen, no aquí.
- **Es la única fuente de verdad del estado del servicio**: el README raíz
  solo enlaza a este fichero; cualquier discrepancia se resuelve aquí.