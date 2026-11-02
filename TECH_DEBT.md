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

Trigger: cuando exista un side effect cross-service real (por ejemplo,
`product-service` consumiendo `list.item.added`) o un broker con requisito de
entrega. La integración del MVP entre ambos servicios es de solo lectura
(`list-service` resuelve el nombre del producto para su snapshot), no una
transacción distribuida, así que no activa por sí sola esta deuda. El ADR
pendiente (`ADR-XXFX-gestion-de-transacciones-distribuidas-con-saga`) la
resolverá.

### Soft mark en `list_item` al borrar un `user_product` referenciado

El borrado de un `user_product` referenciado desde `list_item` deja el
ítem sin resolución de nombre. La recomendación, no vinculante, es
marcar el ítem como "producto no disponible" y preservar el snapshot
`display_name` ([ADR-012](./docs/adr/ADR-012-modelo-productos-base-vs-usuario.md)).

Trigger: la relación de `list-service` con los productos por referencia ya
existe (el MVP); queda pendiente que `product-service` publique el evento
`product.deleted`.

### Clonado user-to-user de productos

No se implementa en el MVP. Cuando exista un modelo de amistad real se
añadirá de forma aditiva `based_on_user_id` nullable con FK a
`user_product.id` y un `CHECK` que lo haga mutuamente excluyente con
`based_on_base_id` ([ADR-012](./docs/adr/ADR-012-modelo-productos-base-vs-usuario.md)).

Trigger: cuando exista modelo de amistad/identidad real (autenticación
real y relaciones sociales).

### Disparador de recientes limitado al toggle de favorito

Mientras no exista el consumo de eventos, el único disparador de
`last_used_at` en recientes es el toggle de favorito; no existe endpoint de
touch explícito
([ADR-013](./docs/adr/ADR-013-favoritos-y-recientes-detalle-de-implementacion.md)).

Trigger: cuando `product-service` **consuma** el evento `list.item.added` de
`list-service` (no basta con que se publique), que será el segundo disparador de
recientes. Ese futuro consumidor necesitará el identificador del actor como campo
aditivo en el payload, hoy no incluido.

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
enforcement todavía. Se activarán cuando exista un modelo real de
compartición (listas colaborativas, relaciones sociales)
([ADR-012](./docs/adr/ADR-012-modelo-productos-base-vs-usuario.md)).

Trigger: se diseña el enforcement de compartición (autenticación real y
modelo de compartición).

### Rate limiting en memoria por instancia

El rate limiting del borde usa un token bucket en memoria, con un cubo por IP
de socket y por proceso. El cupo no se comparte entre instancias ni sobrevive
a un reinicio: con más de una réplica el cupo efectivo se multiplica, y detrás
de un proxy la IP del socket deja de identificar al cliente real. La
limitación está aceptada en
[ADR-017](./docs/adr/ADR-017-api-gateway-propio-frente-a-gateway-gestionado.md).

Trigger: el despliegue pasa a tener más de una réplica del borde o coloca un
proxy delante; entonces se sustituye por un store compartido y una resolución
de IP de confianza.

### Ventana residual en la purga de cubos de rate limiting

La purga periódica de cubos inactivos revalida el estado del cubo antes de
eliminarlo. Entre esa revalidación y la eliminación, una petición concurrente
puede refrescar el cubo justo después y la purga lo borra igualmente; la
siguiente petición lo recrea con la capacidad completa y concede hasta un
cubo completo de tokens extra. El impacto está acotado a un evento por
cubo inactivo durante al menos dos ventanas. No se cierra con `compute` porque
el consumo actualiza el estado con CAS fuera del lock del mapa.

Trigger: se revisa el limitador al introducir un store compartido o si se
observa una ráfaga superior al cupo atribuible a la purga.

## Decisiones opcionales

Decisiones tomadas que admiten una alternativa razonable en el futuro, sin
ser carencias del código actual.

### `basedOnBaseId` ignorado en PATCH

El PATCH de un `user_product` declara `basedOnBaseId` como campo inmutable y
lo ignora en silencio (no aplica cambios, no devuelve error). Alternativa
abierta: devolver `409` si el body intenta modificar el campo
([ADR-012](./docs/adr/ADR-012-modelo-productos-base-vs-usuario.md)).

Trigger: cuando exista un motivo observable para exigir error en vez de
ignorar: feedback de uso, un test/contrato que lo requiera, o al revisar el
contrato Design-First de `user-products`.
