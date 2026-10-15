# Guías de contribución

Este directorio contiene las **convenciones de desarrollo del proyecto**,
redactadas de forma concisa para el equipo de desarrollo.

Cada documento recoge **únicamente la convención tomada**, sin
justificaciones ni trade-offs: los motivos de las decisiones viven en los
[ADRs](../adr/) y en los mensajes de commit.

El índice canónico de todas las guías de contribución del proyecto está en
[`CONTRIBUTING.md`](../../CONTRIBUTING.md).

## Layout del monorepo

El backend vive bajo `services/<servicio>/`; el frontend vive en la raíz
(`frontend/`). Es una convención de layout, no una decisión de arquitectura.
