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

## Deudas técnicas diferidas

Deudas aceptadas de forma consciente durante las fases ya implementadas.
Cada una indica el trigger que la reactiva: hasta que ese trigger se
produzca, la deuda no se retoma.

### Transacciones distribuidas y resiliencia entre `product-service` y `list-service`

La pérdida de transacciones ACID entre servicios es consecuencia directa
del patrón database-per-service ([ADR-002](./docs/adr/ADR-002-database-per-service-pattern.md)).
Una operación que requiera consistencia atómica entre ambos dominios
necesitará un patrón de coordinación explícito: Saga por coreografía u
orquestación, con o sin Transactional Outbox, y mecanismos de resiliencia
ante fallos de red (timeouts, retries, circuit breakers con
Resilience4j).

Trigger: se redactará el ADR pendiente de saga cuando se diseñe la
integración real entre ambos servicios.

### Soft mark en `list_item` al borrar un `user_product` referenciado

El borrado de un `user_product` referenciado desde `list_item` deja el
ítem sin resolución de nombre. La recomendación, no vinculante, es
marcar el ítem como "producto no disponible" y preservar el snapshot
`display_name` ([ADR-012](./docs/adr/ADR-012-modelo-productos-base-vs-usuario.md)).

Trigger: cuando `list-service` implemente la relación con productos por
referencia, y `product-service` decida publicar el evento
`product.deleted`.

### Clonado user-to-user de productos

No se implementa en Fase 1. Cuando exista un modelo de amistad real se
añadirá de forma aditiva `based_on_user_id` nullable con FK a
`user_product.id` y un `CHECK` que lo haga mutuamente excluyente con
`based_on_base_id` ([ADR-012](./docs/adr/ADR-012-modelo-productos-base-vs-usuario.md)).

Trigger: cuando exista modelo de amistad/identidad real (Fase 4 o
posterior).

### Disparador de recientes limitado al toggle de favorito

En Fases 1-3 el único disparador de `last_used_at` en recientes es el
toggle de favorito; no existe endpoint de touch explícito
([ADR-013](./docs/adr/ADR-013-favoritos-y-recientes-detalle-de-implementacion.md)).

Trigger: cuando `list-service` publique el evento de producto añadido a
lista, que será el segundo disparador de recientes.

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