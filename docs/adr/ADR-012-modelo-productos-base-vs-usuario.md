# ADR-012: Modelo de productos base vs usuario

## Estado

Aceptado.

## Contexto

El esquema original de productos dejaba `user_product` reducido a
`owner_id`, `name` y una referencia opcional `based_on`. Durante el
replanteamiento de la colaboración entre listas, de la visibilidad de los
productos personalizados para miembros y amigos del propietario, y de los
campos funcionales necesarios para el catálogo (descripción, categoría,
unidad por defecto y calorías), revisé por completo el modelo.

Documento esta decisión antes de implementar las migraciones de la Rama 4 y
posteriores, porque el diseño ya es concreto y costoso de revertir. Este ADR
formaliza y cierra el placeholder
`ADR-XXF1-modelo-productos-base-vs-usuario` que figuraba como pendiente en
`contexto.md` y en la tabla de ADRs del README.

## Alternativas consideradas

### Una tabla `product` con discriminador `type` e i18n opcional (descartada)

Una única tabla con `type=BASE|USER` mezcla dos agregados con reglas de vida,
permisos e internacionalización diferentes. Obliga a mantener columnas
nullable y constraints cuyo significado depende de `type`, por ejemplo
`owner_id IS NOT NULL` solo para productos `USER`. También hace más lentas de
razonar las queries y degrada la legibilidad del contrato público.

### Tabla común `product` con hijas `base_product` y `user_product` (descartada)

La herencia JOINED no aporta polimorfismo de comportamiento que justifique la
abstracción. Añade dos JOINs a cada lectura y mantiene una relación de
herencia que no representa una regla real del dominio.

### Dos tablas separadas `base_product` y `user_product` (elegida)

Mantiene separados los agregados, sus ciclos de vida, sus permisos y sus
estrategias de internacionalización, y permite que cada contrato sea
explícito.

También tomé estas decisiones anidadas:

- **i18n en `user_product` frente a texto libre monolingüe:** elijo texto
  libre. Un usuario no traduce lo que escribe para una lista personal y
  exigir tres locales al crear un producto custom introduciría fricción
  injustificada. El locale del creador queda fijado como dato del producto.
- **Referencia viva `based_on` con merge frente a snapshot:** elijo snapshot
  copy-on-create con trazabilidad. Es coherente con el Snapshot pattern ya
  aplicado en `list_item.display_name`: los cambios o el borrado del catálogo
  base no afectan al producto derivado. `based_on_base_id` solo audita de qué
  producto nació y es inmutable tras la creación.
- **PK externa solo por id frente a `(product_id, product_type)`:** elijo la
  clave compuesta porque las identidades de `base_product` y `user_product`
  usan secuencias independientes y pueden colisionar. El contrato de
  `list-service` ya aplica este criterio en `AddListItemRequest`.
- **Enum de unidades frente a tabla maestra:** elijo el enum cerrado
  `KG,G,L,ML,UNIT,DOZEN`, representado en Java y SQL como `VARCHAR` con
  `CHECK`. Es reversible y la traducción visual de etiquetas pertenece al
  frontend (`react-i18next`), no al backend.

## Decisión

El modelo tendrá dos agregados y tablas independientes: `base_product` para
el catálogo administrado y `user_product` para los productos creados por un
usuario. Las columnas de `base_product` serán `id`, `code`, `category_id`,
`default_unit`, `calories`, `calories_per`, `is_active` y sus timestamps de
auditoría. Las de `user_product` serán `id`, `owner_id`, `name`, `description`,
`category_id`, `based_on_base_id`, `default_unit`, `calories`, `calories_per`,
`share_with_list_members`, `share_with_friends`, `is_active` y sus timestamps
de auditoría. Respecto al modelo original, el contrato incorpora
`description`, `category_id`, `default_unit`, `calories`, `calories_per`,
`share_with_list_members` y `share_with_friends`. El detalle físico de las
columnas y constraints se documentará en `product-service/docs/database-schema.md`
cuando se escriban las migraciones.

`base_product` conservará la estrategia i18n Table mediante
`base_product_translation`, con PK compuesta `(product_id, locale)`,
`name` obligatorio y `description` nullable. La resolución de locale y el
fallback siguen ADR-011.

`user_product` será monolingüe, con `name` y `description` como columnas
directas. `based_on_base_id` será nullable, tendrá FK a `base_product.id` con
`ON DELETE SET NULL` y será inmutable después de la creación. Al crear desde
un producto base, el servicio copiará los valores del origen y su traducción
resuelta en el `Accept-Language` del creador. No existe una dependencia viva
entre ambos productos.

`share_with_list_members` y `share_with_friends` serán booleanos con
`DEFAULT FALSE`. Product-service los define como preferencias, pero no
construye su enforcement en Fases 1-3: la membresía de listas vive en
`list-service` y los amigos dependerán de un futuro servicio social. La
validación de permisos vivirá en esos servicios cuando exista identidad real
en Fase 4 o posterior. Hasta entonces, los flags son inertes, con el mismo
criterio de placeholder que `owner_id` en ADR-006.

Las secuencias de las dos tablas son independientes. Toda PK externa en
`list_item`, favoritos y recientes será siempre `(product_id, product_type)`;
nunca se usará `product_id` por sí solo. La referencia polimórfica no tendrá
constraint físico entre servicios, siguiendo ADR-002.

El borrado de un `user_product` referenciado desde `list_item` queda como
deuda. La recomendación tentativa, no vinculante en este ADR, es marcar el
ítem como "producto no disponible" y preservar su snapshot
`display_name`. Se diseñará cuando product-service publique `product.deleted`,
pendiente de `ADR-XXF1-eleccion-message-broker` y de la decisión de publicar
ese evento. En esta fase, `docs/events/event-architecture.md` sigue
declarando que product-service no publica eventos.

El clonado user-to-user no se implementa en Fase 1. Cuando exista un modelo
de amistad, se añadirá de forma aditiva `based_on_user_id` nullable con FK a
`user_product.id` y `CHECK (based_on_base_id IS NULL OR based_on_user_id IS NULL)`;
como máximo una de las dos trazabilidades podrá estar poblada.

Favoritos y recientes viven en product-service porque son relaciones
usuario-producto, no usuario-lista. Quedan fuera del alcance inmediato y se
detallarán en `ADR-013-favoritos-y-recientes-detalle-de-implementacion`, que
se redactará cuando llegue su rama. Sus tablas usarán PK
`(user_id, product_id, product_type)` y referencia polimórfica sin constraint
físico, con validación en aplicación.

## Consecuencias

- Los endpoints se dividen en `/base-products` y `/user-products`, en lugar
  de `/products?type=...`; el contrato separa los DTO `BaseProduct` y
  `UserProduct` y elimina el enum `type` sobrecargado del producto unificado.
- `api-contract.yaml` se reescribe solo en la sección de productos; la
  sección de categorías permanece intacta.
- `list_item` no cambia: ya referencia `(productId, productType)` y guarda
  `display_name` como snapshot. ADR-002 y ADR-006 siguen siendo correctos.
- Los flags de compartición permanecen inertes en Fases 1-3. Cualquier
  usuario que conozca un id puede leer o añadir cualquier `user_product` a
  una lista; es una consecuencia explícita del placeholder de ADR-006, no
  una autorización implementada.
- El snapshot protege la presentación histórica aunque cambie el catálogo;
  la publicación de `product.deleted` y el soft mark quedan diferidos.
- La activación de identidad real se documentará en
  `ADR-XXF4-keycloak-como-identity-provider`, y el detalle de favoritos y
  recientes en ADR-013.

La tabla de ADRs y `contexto.md` dejan de listar como pendiente
`ADR-XXF1-modelo-productos-base-vs-usuario`: este ADR-012 lo materializa y lo
cierra. La deuda asociada al borrado de productos referenciados, los flags
inertes y el clonado user-to-user queda anotada como alcance futuro, no como
una decisión pendiente de este documento.
