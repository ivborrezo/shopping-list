# ADR-LS-001: Snapshot del nombre de producto en `list_item` (`display_name`)

## Estado

Aceptado

## Contexto

[ADR-002](../../../../docs/adr/ADR-002-database-per-service-pattern.md) fija que
cada servicio tiene su propia base de datos y que las relaciones entre dominios
no se modelan como claves foráneas, sino como referencias por identificador.
`list_item` referencia un producto de `product-service` por el par
`(productType, productId)`, sin clave foránea real.

Esa decisión deja abierta la pregunta de cómo se muestra el nombre del producto
dentro de una lista sin recurrir a un JOIN distribuido ni a una consulta en vivo
contra `product-service` en cada lectura. Este ADR documenta el mecanismo
elegido —el **Snapshot pattern**—, cierra la referencia colgante de ADR-002 y
acota qué información entra en el snapshot.

## Decisión

`list_item` almacena un snapshot `display_name` con el nombre del producto en el
momento de añadirlo a la lista. El nombre se resuelve **sincrónicamente** contra
`product-service`, reenviando la cabecera `Accept-Language` de la petición; el
valor persistido es monolingüe (el resuelto en ese instante) y no se re-traduce
después.

- **No es redundancia.** El snapshot desacopla la presentación histórica de la
  lista de cambios futuros en el catálogo y evita un JOIN distribuido o una
  llamada en vivo por lectura. Es la materialización del principio de
  Database-per-Service.
- **Qué entra en el snapshot:** el nombre mostrable del producto en el momento
  del alta. **Qué no entra:** precio, categoría, descripción, imagen y cualquier
  otro dato que deba reflejar el estado vivo del catálogo; esos datos se
  consultan a `product-service` bajo demanda si se necesitan.
- **Referencia al producto:** por `(productType, productId)`, donde `productId`
  es el identificador público UUID del producto (ver
  [ADR-016](../../../../docs/adr/ADR-016-estrategia-de-identificadores-publicos.md)).
  El snapshot no almacena ningún identificador interno de `product-service`.
- **Actualización:** el snapshot no se actualiza si el catálogo cambia después
  del alta.

## Consecuencias

### Positivas

- La lista conserva una presentación estable aunque el producto se renombre o
  se reorganice el catálogo.
- Ninguna lectura de una lista necesita llamar a `product-service`.
- Se respeta el aislamiento de datos de cada servicio sin introducir acoplamiento
  de esquema.

### Negativas / Trade-offs aceptados

- El nombre puede quedar desactualizado respecto al catálogo.
- El snapshot es monolingüe: cambiar el idioma de la petición no re-traduce los
  nombres ya persistidos.
- Si el producto referenciado se borra, el ítem conserva su snapshot; el marcado
  de "producto no disponible" queda como deuda técnica abierta.

## Alternativas consideradas

### Resolver el nombre en vivo contra `product-service` en cada lectura

**Por qué se descartó:** introduce latencia y acoplamiento en la ruta de lectura
más frecuente, multiplica las llamadas por elemento y haría que un cambio en el
catálogo alterase listas ya existentes, rompiendo su estabilidad histórica.

### JOIN distribuido contra la base de datos de `product-service`

**Por qué se descartó:** contradice directamente
[ADR-002](../../../../docs/adr/ADR-002-database-per-service-pattern.md): ningún
servicio accede a la base de datos de otro.

### Incluir el precio en el snapshot

**Por qué se descartó:** el precio es un dato vivo que debe reflejar el estado
actual del catálogo, no el del momento del alta; se consulta bajo demanda si se
necesita.

## Documentación relacionada

- **[ADR-002](../../../../docs/adr/ADR-002-database-per-service-pattern.md)** — causa
  raíz del uso del Snapshot pattern.
- **[ADR-016](../../../../docs/adr/ADR-016-estrategia-de-identificadores-publicos.md)** —
  referencia cross-service por `(productType, publicId)`.
- **[ADR-011](../../../../docs/adr/ADR-011-estrategia-de-internacionalizacion-y-fallback.md)** —
  resolución del idioma y fallback del nombre.
- **[`api-contract.yaml`](../api-contract.yaml)** — el campo `displayName` en
  `ListItem`.
