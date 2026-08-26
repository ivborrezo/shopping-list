# Plantilla de overview de servicio

> Esta plantilla define la estructura del `docs/overview.md` de cada
> servicio. Para usarla: copia este fichero como
> `services/<servicio>/docs/overview.md` y rellena cada sección con el
> estado real del servicio, sustituyendo los textos en cursiva.
>
> Los enlaces relativos apuntan a los documentos del propio servicio
> (`./api-contract.yaml`, `./database-schema.md`); se resuelven desde el
> destino, no desde este directorio.

---

<!-- A partir de aquí, el contenido es la plantilla en sí. Al copiarla,
     borra este bloque de instrucciones y deja el resto. -->

# Overview de <servicio>

> _Mapa de estado del servicio `<servicio>`: qué hace, qué expone y qué
> tiene pendiente. Este fichero enlaza a los documentos de detalle en lugar
> de repetirlos; no es un volcado._

## Qué hace

_Una o dos frases: propósito del servicio y su responsabilidad dentro del
sistema._

## Endpoints implementados

- _`MÉTODO /ruta` — descripción breve de la operación._
- _`MÉTODO /ruta` — descripción breve de la operación._

> El detalle del contrato (parámetros, respuestas, errores) vive en
> [`api-contract.yaml`](./api-contract.yaml); aquí solo se lista qué hay
> implementado.

## Tablas y migraciones

_Resumen de las tablas del servicio y del estado de las migraciones. El
esquema detallado vive en [`database-schema.md`](./database-schema.md)._

## Dependencias

- _Servicios: nombre y uso._
- _Infraestructura: base de datos, colas, etc._

## Deudas abiertas

- _Deuda — contexto breve y, si se sabe, plan para resolverla._
- _Deuda — contexto breve y, si se sabe, plan para resolverla._

## Fuera de alcance

_Responsabilidades que el servicio no cubre, ahora o por diseño._

## Reglas de uso

- **`overview.md` es un mapa, no un volcado**: enlaza a los documentos de
  detalle en lugar de repetirlos. Si el detalle cambia, se actualiza en su
  documento de origen, no aquí.
- **Es la única fuente de verdad del estado del servicio**: el README raíz
  solo enlaza a este fichero; cualquier discrepancia se resuelve aquí.