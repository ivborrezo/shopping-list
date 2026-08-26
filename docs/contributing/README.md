# Guías de contribución

Este directorio contiene las **convenciones de desarrollo del proyecto**,
redactadas de forma concisa para el equipo de desarrollo.

Cada documento recoge **únicamente la convención tomada**, sin
justificaciones ni trade-offs: los motivos de las decisiones viven en los
[ADRs](../adr/) y en los mensajes de commit.

| Tema | Documento |
|---|---|
| Setup completo del repositorio | [setup.md](./setup.md) |
| Estilo de commits | [commits.md](./commits.md) |
| Ramas y flujo git | [branches.md](./branches.md) |
| Estilo de código y linters | [code-style.md](./code-style.md) |
| Testing | [testing.md](./testing.md) |
| Convenciones de logging | [logging.md](./logging.md) |
| Entorno local | [local-environment.md](./local-environment.md) |
| Plantilla de overview de servicio | [service-overview-template.md](./service-overview-template.md) |

## Layout del monorepo

El backend vive bajo `services/<servicio>/`; el frontend vive en la raíz
(`frontend/`). Es una convención de layout, no una decisión de arquitectura.

El índice de contribución del proyecto es [`CONTRIBUTING.md`](../../CONTRIBUTING.md).
