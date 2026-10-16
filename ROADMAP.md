# Roadmap

> Este documento es la **única fuente numerada** de las fases del proyecto.
> El resto de la documentación (ADRs, overviews, eventos, CI/CD) se refiere a
> las fases de forma descriptiva, nunca por su número, para no acoplarse a una
> numeración que puede cambiar. Ver
> [`docs/contributing/documentation.md`](./docs/contributing/documentation.md).

## Fases

| Fase | Estado | Contenido |
|---|---|---|
| **Fase 0** | Completada | Estructura del monorepo, README inicial y documentación macro del sistema (arquitectura C4, ADRs, eventos, convenciones). |
| **Fase 1** | Completada | MVP Core completo: `product-service` (categories, base-products, user-products, favoritos y recientes) y `list-service` (listas, ítems, integración de solo lectura con `product-service` y publicación de eventos). Overviews: [product-service](./services/product-service/docs/overview.md), [list-service](./services/list-service/docs/overview.md). |
| **Fase 2** | Sin empezar | Plataforma: `api-gateway` (Spring Cloud Gateway: enrutamiento, rate limiting, CORS, propagación de `Accept-Language`) y `config-service` (Spring Cloud Config Server) como última parte de la fase. |
| **Fase 3** | Sin empezar | Seguridad: `auth-service` como Identity Provider (Keycloak + OAuth2/OIDC). Sustituye el placeholder `ownerId` de las fases anteriores. |
| **Fase 4** | Sin empezar | Frontend: `frontend/` en la raíz del repositorio (React, i18n de UI con react-i18next, PWA), construido contra el API Gateway y la seguridad ya existentes. |
| **Fase 5** | Sin empezar | Despliegue: puesta en producción de la arquitectura sobre infraestructura dentro del Free Tier. Alcance concreto abierto hasta que llegue la fase. |
| **Fase 6** | Sin empezar | Comunicación asíncrona y notificaciones: `notification-service` (Node.js, consumidor de eventos, email / web push / webhooks). |
| **Fase 7** | Sin empezar | Observabilidad: OpenTelemetry + Micrometer + Prometheus + Grafana + Loki, self-hosted en Docker Compose. |

## Alcance diferido

Funcionalidad futura que no es una carencia del código actual y que se añadirá
de forma aditiva cuando llegue su fase:

- **Colaboración y membresía de listas.** La compartición de listas entre
  colaboradores queda fuera del MVP, sin campos reservados en el contrato de
  `list-service`; se incorporará con el modelo real de identidad (Keycloak).

## Deuda técnica

La deuda técnica aceptada de forma consciente durante las fases ya
implementadas vive en [TECH_DEBT.md](./TECH_DEBT.md).

## ADRs pendientes

| Código | Decisión pendiente |
|---|---|
| `ADR-XXF6` | Elección de message broker (Kafka vs RabbitMQ). Define el canal físico de transporte de eventos; la publicación se mantiene desacoplada del broker vía Ports and Adapters ([event-architecture](./docs/events/event-architecture.md)). |
| `ADR-XXF3` | Keycloak como Identity Provider. Superará al ADR-006, que queda como registro histórico del placeholder `ownerId` ([ADR-006](./docs/adr/ADR-006-identificacion-propietario-sin-autenticacion.md)). |
| `ADR-XXFX` | Gestión de transacciones distribuidas con Saga. Resolverá la deuda técnica aceptada explícitamente en [ADR-002](./docs/adr/ADR-002-database-per-service-pattern.md). |
| `ADR-XXXX-configuracion-cloud-map-ecs` | Configuración de Service Discovery en producción con AWS Cloud Map + ECS. Desarrolla el principio de DNS interno ya fijado en [ADR-003](./docs/adr/ADR-003-service-discovery-nativo-y-descarte-de-eureka.md). |

## Servicios futuros (sin crear directorios aún)

Los siguientes servicios se referencian aquí, en el README y en los
diagramas de arquitectura, pero **no se crean sus directorios hasta que
llegue su fase correspondiente** (YAGNI): `api-gateway`,
`config-service`, `auth-service`, `notification-service` y `frontend`.

Crear el directorio de un servicio antes de su fase añadiría estructura
sin implementación que validar ni desplegar; cada servicio se incorpora
al monorepo cuando su fase lo requiera.
