# Deuda técnica

Catálogo de deudas técnicas aceptadas de forma consciente durante las fases
ya implementadas. Cada una indica el trigger que la reactiva: hasta que ese
trigger se produzca, la deuda no se retoma.

El alcance diferido (funcionalidad futura que no es una carencia del código
actual) no vive aquí: está en el [ROADMAP](./ROADMAP.md).

## Deudas técnicas

### Transacciones distribuidas y resiliencia entre `product-service` y `list-service`

La pérdida de transacciones ACID entre servicios es consecuencia directa
del patrón database-per-service ([ADR-002](./docs/adr/ADR-002-database-per-service-pattern.md)).
Una operación que requiera consistencia atómica entre ambos dominios
necesitará un patrón de coordinación explícito: Saga por coreografía u
orquestación, con o sin Transactional Outbox, y mecanismos de resiliencia
ante fallos de red (timeouts, retries, circuit breakers con
Resilience4j).

Trigger: se redactará el ADR pendiente de saga cuando se diseñe la
integración real entre ambos servicios.

### Soft mark en `list_item` al borrar un `user_product` referenciado

El borrado de un `user_product` referenciado desde `list_item` deja el
ítem sin resolución de nombre. La recomendación, no vinculante, es
marcar el ítem como "producto no disponible" y preservar el snapshot
`display_name` ([ADR-012](./docs/adr/ADR-012-modelo-productos-base-vs-usuario.md)).

Trigger: cuando `list-service` implemente la relación con productos por
referencia, y `product-service` decida publicar el evento
`product.deleted`.

### Clonado user-to-user de productos

No se implementa en Fase 1. Cuando exista un modelo de amistad real se
añadirá de forma aditiva `based_on_user_id` nullable con FK a
`user_product.id` y un `CHECK` que lo haga mutuamente excluyente con
`based_on_base_id` ([ADR-012](./docs/adr/ADR-012-modelo-productos-base-vs-usuario.md)).

Trigger: cuando exista modelo de amistad/identidad real (Fase 4 o
posterior).

### Disparador de recientes limitado al toggle de favorito

En Fases 1-3 el único disparador de `last_used_at` en recientes es el
toggle de favorito; no existe endpoint de touch explícito
([ADR-013](./docs/adr/ADR-013-favoritos-y-recientes-detalle-de-implementacion.md)).

Trigger: cuando `list-service` publique el evento de producto añadido a
lista, que será el segundo disparador de recientes.

### N+1 en la resolución de nombres localizados

La resolución del nombre (y la descripción) localizado se hace por fila en
capa de servicio, lo que genera una consulta adicional por elemento en los
listados paginados (N+1). Aceptado con el volumen actual del catálogo; si el
volumen de categorías/productos o de idiomas creciera hasta hacerlo medible,
se reevaluará con `@EntityGraph` o `JOIN FETCH`
([ADR-011](./docs/adr/ADR-011-estrategia-de-internacionalizacion-y-fallback.md)).

Trigger: el N+1 se vuelve medible con datos reales.

### Flags de compartición inertes

`share_with_list_members` y `share_with_friends` se persisten pero no tienen
enforcement en las Fases 1-3. Se activarán cuando exista un modelo real de
compartición (listas colaborativas, relaciones sociales)
([ADR-012](./docs/adr/ADR-012-modelo-productos-base-vs-usuario.md)).

Trigger: se diseña el enforcement de compartición (Fase 4 o posterior).

## Decisiones opcionales

Decisiones tomadas que admiten una alternativa razonable en el futuro, sin
ser carencias del código actual.

### `basedOnBaseId` ignorado en PATCH

El PATCH de un `user_product` declara `basedOnBaseId` como campo inmutable y
lo ignora en silencio (no aplica cambios, no devuelve error). Alternativa
abierta: devolver `409` si el body intenta modificar el campo
([ADR-012](./docs/adr/ADR-012-modelo-productos-base-vs-usuario.md)).

Trigger: se decide ser estricto en lugar de ignorar en silencio.
