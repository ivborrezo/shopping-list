# Roadmap

## Fases

| Fase | Estado | Contenido |
|---|---|---|
| **Fase 0** | Completada | Estructura del monorepo, README inicial y documentación macro del sistema (arquitectura C4, ADRs, eventos, convenciones). |
| **Fase 1** | En desarrollo | MVP Core. `product-service` implementado: categories, base-products, user-products, favoritos y recientes. `list-service` pendiente de implementación: placeholder con contrato definido ([overview](./services/list-service/docs/overview.md), `api-contract.yaml`). |
| **Fase 2** | Sin empezar | Frontend: `frontend/` en la raíz del repositorio (React, i18n de UI con react-i18next, PWA). |
| **Fase 3** | Sin empezar | Infraestructura de plataforma: `api-gateway` (Spring Cloud Gateway: enrutamiento, rate limiting, CORS, propagación de `Accept-Language`) y `config-service` (Spring Cloud Config Server). |
| **Fase 4** | Sin empezar | Seguridad: `auth-service` como Identity Provider (Keycloak + OAuth2/OIDC). Sustituye el placeholder `ownerId` de las Fases 1-3. |
| **Fase 5** | Sin empezar | Comunicación asíncrona y notificaciones: `notification-service` (Node.js, consumidor de eventos, email / web push / webhooks). |
| **Fase 6** | Sin empezar | Observabilidad: OpenTelemetry + Micrometer + Prometheus + Grafana + Loki, self-hosted en Docker Compose. |

## Deuda técnica

La deuda técnica aceptada de forma consciente durante las fases ya
implementadas vive en [TECH_DEBT.md](./TECH_DEBT.md).

## ADRs pendientes

| Código | Decisión pendiente |
|---|---|
| `ADR-XXF1` | Elección de message broker (Kafka vs RabbitMQ). Define el canal físico de transporte de eventos; la publicación se mantiene desacoplada del broker vía Ports and Adapters ([event-architecture](./docs/events/event-architecture.md)). |
| `ADR-XXF4` | Keycloak como Identity Provider. Superará al ADR-006, que queda como registro histórico del placeholder `ownerId` ([ADR-006](./docs/adr/ADR-006-identificacion-propietario-sin-autenticacion.md)). |
| `ADR-XXFX` | Gestión de transacciones distribuidas con Saga. Resolverá la deuda técnica aceptada explícitamente en [ADR-002](./docs/adr/ADR-002-database-per-service-pattern.md). |

## Servicios futuros (sin crear directorios aún)

Los siguientes servicios se referencian aquí, en el README y en los
diagramas de arquitectura, pero **no se crean sus directorios hasta que
llegue su fase correspondiente** (YAGNI): `api-gateway`,
`config-service`, `auth-service`, `notification-service` y `frontend`.

Crear el directorio de un servicio antes de su fase añadiría estructura
sin implementación que validar ni desplegar; cada servicio se incorpora
al monorepo cuando su fase lo requiera.