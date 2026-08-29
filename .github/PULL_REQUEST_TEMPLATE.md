# Pull Request

## Descripción

_Resumen breve de qué resuelve esta PR y por qué. Si cierra una issue,
referénciala (`Closes #N`)._

## Cambios

- _Cambios principales, agrupados por servicio o área afectada._
- _Usa el mismo estilo que los mensajes de commit (Conventional Commits)._

## Checklist

- [ ] Tests unitarios e integración en verde en local
- [ ] `mvn clean verify` (o equivalente) ejecutado en local
- [ ] Linters y formatters en verde (Spotless / Checkstyle, o el
      equivalente del lenguaje)
- [ ] CI en verde en esta rama
- [ ] **Actualicé `services/<svc>/docs/overview.md` si cambié contrato,
      esquema o endpoints** (`api-contract.yaml` / `database-schema.md`
      si aplica), siguiendo la
      [plantilla de overview](docs/contributing/service-overview-template.md)
- [ ] Título de la PR siguiendo Conventional Commits (ver
      [commits.md](docs/contributing/commits.md))

> Este checklist es una guía de revisión, no un gate técnico: los ítems
> no se verifican automáticamente en CI.