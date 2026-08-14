# Estilo de commits

Los mensajes de commit siguen **Conventional Commits** en castellano:

```
tipo(scope): resumen
<cuerpo opcional explicando el contexto y el porqué>
```

- `tipo`: `feat | fix | docs | test | chore | build | ci | refactor`.
- `scope` (opcional): en minúsculas; suele ser el servicio afectado
  (`product-service`, `list-service`, `readme`).
- `resumen`: verbo en infinitivo, corto y descriptivo.

Ejemplos:

- `feat(product-service): implementar listado de recientes`
- `docs(readme): actualizar estado tras Rama 6`
- `test(product-service): tests red para listado de favoritos`

El hook `commit-msg` valida este formato en cada commit (activación en el
[setup](setup.md)).

Referencia: [ADR-004](../adr/ADR-004-estandares-de-desarrollo-y-gobernanza.md).
