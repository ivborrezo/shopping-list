# Ramas y flujo git

- Una rama por funcionalidad: `feature/<servicio>-<descripcion>`.
- El trabajo avanza en la rama con push periódico.
- Al finalizar, se abre un **pull request de cierre** a `main` y se fusiona
  mediante merge.
- `main` es la rama protegida: no se commitea directamente sobre ella.

Los mensajes de commit se validan con los hooks del repositorio, activados
durante el [setup](setup.md).

Referencia: [ADR-009](../adr/ADR-009-estrategia-de-ci-y-git-hooks.md) ·
[Estrategia de CI/CD](../cicd/cicd-strategy.md).
