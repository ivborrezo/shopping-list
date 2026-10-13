# Convenciones de redacción de ADR

- Un ADR es un **registro inmutable**: una vez aceptado, no se reescribe su
  cuerpo.
- El **estado** de una decisión se define en dos sitios: el campo `Estado`
  del ADR (`Aceptado` / `Redactado`) y el registro vivo
  ([`ROADMAP.md`](../../ROADMAP.md) → "ADRs pendientes"). La tabla de ADRs del
  README **refleja** ese estado, no lo define: ante cualquier discrepancia,
  manda el campo `Estado` del ADR. El cuerpo del ADR no describe estado.
- **Referencias hacia delante**: permitidas para trazabilidad (nombrar una
  decisión futura que refinará o supersederá la actual), pero **por tema**, no
  por estado.
- **Prohibido** afirmar estado volátil en la prosa: "(pendiente de redactar)",
  "aún no existe", "en el futuro", etc.
- **Correcciones**: las erratas factuales objetivas (rutas o nombres mal
  escritos) se corrigen; las afirmaciones que eran ciertas al redactarse y han
  caducado **no se reescriben**: se dejan o se añaden como **nota aditiva**.
- **Citas**: nunca se edita dentro de una cita literal de otro ADR.

## Estados

- **`Redactado`** — el ADR está escrito, pero la decisión todavía no se ha
  ratificado; el cuerpo puede seguir revisándose.
- **`Aceptado`** — la decisión se ratifica como firme; a partir de aquí el
  cuerpo es inmutable (solo erratas factuales o notas aditivas / `Enmienda`).

Referencia: [ADR-004](../adr/ADR-004-estandares-de-desarrollo-y-gobernanza.md)
(gobernanza).
