# ADR-013: Favoritos y recientes (detalle de implementación)

## Estado

Redactado

## Contexto

[ADR-012](ADR-012-modelo-productos-base-vs-usuario.md) fijó que favoritos y
recientes viven en `product-service` porque son relaciones usuario-producto,
no usuario-lista, y dejó aplazado a este ADR el detalle de su implementación.
Llega el momento de cerrarlo: las tablas `user_favorite_product` y
`user_recent_product` se incorporan al esquema del servicio en esta rama, y
con ellas las decisiones que las modelan. Documento aquí lo que decido, no lo
que el código permite hacer de forma obvia.

El modelo hereda dos restricciones ya decididas que condicionan todo lo que
sigue:

- Las identidades de `base_product` y `user_product` usan secuencias
  independientes (ADR-012), así que `product_id` por sí solo no identifica
  un producto: la referencia polimórfica necesita el discriminante
  `product_type` (`BASE` | `USER`). La PK de ambas tablas es, por tanto,
  `(user_id, product_id, product_type)`.
- No existe una "tabla de productos" única a la que apuntar con una FK
  física, ni conviene crearla: entre agregados del mismo servicio tampoco se
  mezclan bounded contexts. Se replica fielmente el patrón
  database-per-service (ADR-002), donde la integridad referencial entre
  dominios se resuelve por validación en capa de aplicación, no por
  constraints del motor. El precedente es `list_item` en `list-service`, que
  ya referencia `(productId, productType)` sin FK.

`user_id` y `owner_id` siguen el placeholder de
[ADR-006](ADR-006-identificacion-propietario-sin-autenticacion.md): un UUID
plano en el body, validado solo en formato, sin tabla de usuarios que lo
respalde.

## Decisiones

### Decisión 1 — PK compuesta `(user_id, product_id, product_type)` sin FK física

`user_favorite_product` y `user_recent_product` usan PK compuesta
`(user_id, product_id, product_type)` y no tienen ninguna FK hacia las tablas
de productos. Elijo esta PK porque el par usuario-producto es la identidad
natural de la relación, y prescindo de la FK física porque el modelo lo
hereda de ADR-012 y ADR-002. La validación de que el producto referenciado
existe se hace en la capa de aplicación del servicio: el toggle de favorito
resuelve el producto según su `productType` y devuelve `404` si no existe.

Esta decisión tiene dos consecuencias operativas:

- Si un producto se borra, la fila de favorito o reciente que lo referencia
  queda huérfana. En el listado de favoritos se resuelve el nombre del
  producto y, si ya no existe, se devuelve `name: null`. La fila no se filtra
  del resultado: descartarla corrompería la paginación, que contaría filas
  que luego no serviría.
- El borrado de una fila de favorito se hace siempre por clave mediante JPQL
  directo, nunca por `EntityManager.remove()`. El `remove` de Hibernate 7.4.1
  con claves compuestas `@IdClass` no resuelve correctamente el identificador
  y provoca un fallo en runtime; el borrado por query lo evita de raíz.

### Decisión 2 — `last_used_at` como criterio de ordenación de recientes

`user_recent_product` guarda `last_used_at`. El listado de recientes devuelve
el top 10 ordenado por `last_used_at DESC`.

El servicio escribe el timestamp explícitamente al marcar la interacción; no
delego solo en el `DEFAULT CURRENT_TIMESTAMP` de la columna. La columna
mantiene el default como red de seguridad para inserciones, pero la marca
efectiva que se sirve y se ordena la pone la capa de aplicación, que controla
el valor exacto y evita depender del reloj de la base de datos.

### Decisión 3 — Disparador de `last_used_at` limitado al toggle de favorito en Fases 1-3

Al **marcar** un favorito se actualiza —o se inserta, si no existe— la fila
de reciente del mismo usuario y producto, refrescando su `last_used_at`. Al
**desmarcar** un favorito no se toca la fila de reciente: la desmarcación no
es una interacción con el producto y no debe desplazarlo en el ranking.

Este es el único disparador de recientes en Fases 1-3. Cuando `list-service`
publique el evento "producto añadido a lista" existirá un segundo disparador
para esas interacciones; queda pendiente de la elección del message broker,
no de una decisión que tome aquí.

### Decisión 4 — Sin endpoint de touch explícito

No existe endpoint para actualizar `last_used_at` a mano. Es una deuda
consciente, documentada aquí mismo: los únicos caminos hacia la marca de
reciente son el toggle de favorito (Decisión 3) y, en el futuro, el disparador
de `list-service`. La decisión se reevaluará cuando ese segundo disparador
exista y se conozca el volumen real de interacciones que lo justificarían.

## Consecuencias

### Positivas

- El modelo replica el patrón ya probado en `list_item`: referencia
  polimórfica sin FK, integridad en capa de aplicación, sin acoplamiento al
  ciclo de vida de `base_product` o `user_product`. No hay migración que
  coordinar cuando cambie el esquema de productos.
- Entidades `@IdClass` planas, sin `@MapsId` ni asociaciones JPA: `user_id`
  es un UUID plano y la referencia a producto es polimórfica sin FK. El
  mapeo es directo y no arrastra el coste de un grafo de objetos que no
  representa ninguna relación real.

### Negativas / Trade-offs aceptados

- **Filas huérfanas al borrar un producto**: sin FK no hay cascada ni
  `ON DELETE`, así que el borrado de un producto deja atrás sus favoritos y
  recientes. Se mitiga a nivel de presentación (`name: null` en el listado)
  y se acepta la fila residual en base de datos.
- **El toggle de un producto inexistente devuelve 404**, lo que impide
  desmarcar un favorito huérfano: el usuario no puede limpiar la fila de un
  producto que ya no existe. Es una limitación aceptada del mismo
  mecanismo de validación que protege el alta.
- **Coste de resolución de nombres en el listado**: resolver el nombre de
  cada favorito implica un acceso por fila (no hay JOIN posible sin FK). Con
  los volúmenes esperados el coste es despreciable; se acepta sin
  optimización prematura.

## Alternativas consideradas

### FK física a una tabla de productos (descartada)

Una FK hacia una tabla unificada de productos, o hacia `base_product` o
`user_product` según el tipo.

**Por qué se descartó:** la fila de favorito o reciente no sabe a qué tabla
apunta sin leer `product_type`, así que una FK estática no puede expresar el
origen sin añadir lógica de discriminación por fila que PostgreSQL no
soporta de forma natural. Además mezclaría agregados de un mismo servicio y
rompería el precedente de `list_item`, que ya convive con la referencia
polimórfica sin constraint físico. La integridad se resuelve donde siempre se
ha resuelto en este proyecto: en la capa de aplicación.

### PK surrogate (`id` serial) en las tablas de favorito y reciente (descartada)

Un `id` generado como PK y una `UNIQUE` sobre `(user_id, product_id,
product_type)`.

**Por qué se descartó:** el par usuario-producto es la identidad natural de
ambas relaciones; una clave surrogate obligaría a mantener una unique extra
solo para evitar duplicados y complicaría el borrado por clave que exige la
Decisión 1. La PK compuesta expresa directamente lo que la tabla es.

### Disparador de `last_used_at` en todas las interacciones con producto o endpoint de touch (descartada)

Marcar recientes en cada interacción con un producto (crear, modificar,
toggle...) y/o exponer un endpoint para refrescar la marca a mano.

**Por qué se descartó:** amplía la superficie de cambio sin un consumidor
real que la justifique en Fases 1-3. El único consumidor actual es el ranking
de recientes y basta con los dos caminos de la Decisión 3; un endpoint de
touch adicional no aportaría nada que el toggle no cubra y añadiría contrato
a mantener.

## Documentación relacionada

- **[ADR-002](ADR-002-database-per-service-pattern.md)** — por qué no puede
  existir una FK física hacia los productos de otro servicio y por qué la
  integridad se resuelve por validación en aplicación.
- **[ADR-006](ADR-006-identificacion-propietario-sin-autenticacion.md)** —
  el placeholder `owner_id`/`user_id` sin tabla de usuarios que respalde el
  identificador.
- **[ADR-012](ADR-012-modelo-productos-base-vs-usuario.md)** — el modelo de
  productos base vs usuario que motiva la PK externa compuesta
  `(product_id, product_type)` y que difiere a este ADR el detalle de
  favoritos y recientes.
- **[`product-service/docs/database-schema.md`](../product-service/docs/database-schema.md)** —
  detalle físico de las tablas `user_favorite_product` y
  `user_recent_product` (migraciones V10 y V11).
