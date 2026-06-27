# C4 — Nivel 2: Diagrama de Contenedores (ShoppingList)

> **Estado del documento:** Borrador (no definitivo). Sujeto a revisión
> a medida que se resuelvan decisiones pendientes (ver sección
> "Decisiones abiertas reflejadas en este diagrama").

## Propósito de este documento

Este diagrama representa el **Nivel 2 del modelo C4** (Containers) para
el sistema ShoppingList: descompone el sistema en los bloques de
ejecución de alto nivel (aplicaciones, servicios, bases de datos) y
muestra cómo se comunican entre sí y con los sistemas externos de los
que depende.

A diferencia del Nivel 1 (Contexto), que trata el sistema como una caja
negra, este nivel abre esa caja y expone su composición interna — pero
sin descender al detalle de clases, módulos o tablas (eso corresponde a
niveles inferiores y a la documentación específica de cada servicio en
`<servicio>/docs/`).

### Alcance: arquitectura objetivo, no solo lo ya implementado

Este diagrama representa la **arquitectura objetivo completa** del
sistema, tal como está definida en el roadmap por fases, no únicamente
los contenedores ya desplegados. Se ha tomado esta decisión de alcance
deliberadamente, en lugar de limitar el diagrama al estado actual de
implementación, por las siguientes razones:

- Permite comunicar de un vistazo la visión arquitectónica completa del
  sistema, algo especialmente valioso en un proyecto con propósito de
  portafolio técnico.
- Evita tener que mantener múltiples versiones del mismo diagrama a
  medida que avanza cada fase.

Para que esta decisión no comprometa la honestidad técnica del
documento, **cada contenedor está codificado por color según su estado
real de implementación** (ver leyenda). Ningún elemento del diagrama
debe interpretarse como "ya desplegado" salvo que su color indique
explícitamente lo contrario.

### Lo que este diagrama NO muestra

Por coherencia con el nivel de detalle de C4 Nivel 2, este documento
**excluye deliberadamente**:

- **Esquemas de tablas o relaciones entre tablas** de cada base de
  datos. Cada base de datos aparece como un único contenedor con su
  tecnología y responsabilidad a alto nivel; el detalle relacional
  vive en `<servicio>/docs/database-schema.md`.
- **El stack de observabilidad** (OpenTelemetry, Prometheus, Grafana,
  Grafana Loki). Se trata de infraestructura transversal (*cross-cutting
  concern*) que opera sobre todos los contenedores por igual. Incluirla
  aquí obligaría a trazar una arista de cada contenedor hacia cada
  componente de observabilidad, degradando la legibilidad del diagrama
  sin aportar información sobre el funcionamiento de negocio del
  sistema. Esta infraestructura se documentará en
  `docs/architecture/deployment-topology.md` (Fase 6).
- **Distinción entre roles de usuario** (usuario estándar vs.
  administrador). Ambos perfiles acceden al sistema por el mismo canal
  y protocolo; la diferencia es una cuestión de autorización a nivel de
  rol (Fase 4, Keycloak), no de arquitectura de contenedores.

---

## Diagrama

> **Herramienta de generación:** este diagrama se ha producido con
> **draw.io** como imagen estática (`.svg`), tras descartar Mermaid por
> insuficiente calidad visual para un documento de portafolio. Esta
> decisión se documentará formalmente en su correspondiente ADR de
> herramientas/gobernanza, junto con el resto de estándares de
> documentación del proyecto.
>
> **Trade-off asumido:** a diferencia de un diagrama en texto
> (Mermaid/PlantUML), los cambios sobre este `.svg` **no son legibles
> como diff de Git** — un PR que actualice el diagrama mostrará un
> archivo binario/XML reemplazado, no un cambio de texto inspeccionable
> línea a línea. Se mitiga parcialmente conservando el archivo fuente
> editable (`c4-level2-containers.drawio`) junto al `.svg` exportado,
> de forma que el diagrama siga siendo reproducible y versionado,
> aunque no diffable en el sentido estricto.

![C4 Nivel 2 — Diagrama de Contenedores de ShoppingList](img/c4-level2-containers.svg)

*Fuente editable: [`img/c4-level2-containers.drawio`](img/c4-level2-containers.drawio)*

### Matriz de Comunicaciones e Interacciones

> Esta tabla complementa al diagrama y es la **fuente de verdad textual**
> para el protocolo, el verbo y el tipo de cada relación. Mantenerla
> aparte del `.svg` tiene una ventaja adicional ahora que el diagrama es
> una imagen estática: esta tabla sigue siendo texto plano, por lo que
> **sí es diffable en Git**, y permite revisar cambios en el contrato de
> comunicaciones de un PR sin depender de abrir el archivo de imagen.

| Origen | Destino | Tipo | Protocolo | Descripción |
|---|---|---|---|---|
| Usuario | React App | Síncrono | HTTPS | El usuario interactúa con la interfaz web. |
| React App | API Gateway | Síncrono | REST/HTTPS | Consumo de las APIs del backend. |
| React App | Auth Service | Síncrono | OIDC/HTTPS | Inicia el flujo de login (redirección OIDC). |
| API Gateway | product-service | Síncrono | REST/HTTPS | Enrutamiento de peticiones. |
| API Gateway | list-service | Síncrono | REST/HTTPS | Enrutamiento de peticiones. |
| API Gateway | Auth Service | Síncrono | OIDC/HTTPS | Validación de tokens de acceso. |
| Config Service | product-service | Configuración | REST/HTTPS | Sirve configuración centralizada por entorno. |
| Config Service | list-service | Configuración | REST/HTTPS | Sirve configuración centralizada por entorno. |
| Config Service | API Gateway | Configuración | REST/HTTPS | Sirve configuración centralizada por entorno. |
| Config Service | Auth Service | Configuración | REST/HTTPS | Sirve configuración centralizada por entorno. |
| Config Service | Notification Service | Configuración | REST/HTTPS | Sirve configuración centralizada por entorno. |
| Auth Service | auth-db | Síncrono | JDBC/SQL | Lectura/escritura de usuarios, credenciales, roles y realms. |
| Auth Service | OAuth Provider | Síncrono | OIDC/HTTPS | Delega la autenticación en un proveedor externo (ej. Google). |
| product-service | product-db | Síncrono | JDBC/SQL | Lectura/escritura del catálogo de productos y categorías. |
| list-service | list-db | Síncrono | JDBC/SQL | Lectura/escritura de listas e ítems de lista. |
| list-service | product-service | Síncrono | REST/HTTPS | Consulta puntual de producto referenciado (sin join entre BDs). |
| list-service | Message Broker | Asíncrono | AMQP / Kafka protocol | Publicación de eventos de dominio (lista creada, producto añadido). |
| product-service | Message Broker | Asíncrono | AMQP / Kafka protocol | Publicación de eventos de dominio. |
| Message Broker | Notification Service | Asíncrono | AMQP / Kafka protocol | Consumo de eventos para generar notificaciones. |
| Notification Service | SendGrid | Síncrono | SMTP / HTTPS API | Envío de notificaciones por email. |

| Color | Significado |
|---|---|
| 🟢 Verde | **Implementado** — contenedor activo en el sistema actual (Fase 1: product-service, list-service y sus bases de datos) |
| 🟡 Amarillo | Roadmap — Frontend (Fase 2: React App) |
| 🔵 Azul | Roadmap — Plataforma (Fase 3: API Gateway, Config Service) |
| 🟣 Morado | Roadmap — Seguridad (Fase 4: Auth Service y su base de datos) |
| 🟠 Naranja | Roadmap — Mensajería y notificaciones (Fase 5: Message Broker, Notification Service) |
| ⚫ Gris (borde discontinuo) | Sistema externo, fuera del límite del sistema ShoppingList |
| ⬛ Pizarra | Actor humano |

Las líneas discontinuas en el diagrama indican relaciones de
**soporte/configuración** (ej. Config Service sirviendo configuración,
list-service consultando producto por referencia) en contraste con las
líneas continuas, que indican el **flujo principal de la petición o del
evento**.

---

## Notas por contenedor

### React App
Roadmap — Fase 2 (Frontend, aún no implementado). Será el único punto
de interacción directo del usuario con el sistema. En este nivel de
detalle no se distingue entre la futura SPA y su evolución hacia PWA
(documentada como roadmap en la descripción del proyecto); ambas
comparten el mismo contenedor lógico.

### API Gateway
Una vez implementado (Fase 3), se convertirá en el único punto de
entrada del Frontend hacia el backend, asumiendo responsabilidades hoy
inexistentes como rate limiting y CORS centralizado. Aplica el patrón
**API Gateway**, y es el punto natural donde se integrará la validación
de tokens de Auth Service en Fase 4.

### Config Service
Aplica el patrón **Externalized Configuration**. Sus relaciones con el
resto de servicios se representan con línea discontinua porque no forma
parte del flujo de negocio: es infraestructura de arranque/configuración
consultada por cada servicio, no un colaborador en el procesamiento de
una petición de usuario.

### Auth Service (Keycloak)
Aplica el patrón **Identity Provider externalizado**. Aunque se muestra
con una única caja, internamente Keycloak gestiona su propio modelo de
roles y realms — ese detalle no corresponde a este nivel. Su
comunicación con el OAuth Provider externo (ej. Google) ilustra el
patrón de **Federated Identity / Social Login**.

Nótese que la relación de login parte de **React App**, no del
**Usuario** directamente: en OIDC es la aplicación cliente la que
inicia el flujo de autenticación (normalmente mediante una
redirección del navegador), no el usuario interactuando con Keycloak
como un sistema aparte. El usuario solo percibe una pantalla de login
dentro de la experiencia de la propia aplicación.

### product-service / list-service
Aplican **Database-per-Service** (ADR-002): cada uno posee su base de
datos exclusiva, sin joins directos entre ellas. La relación
`list-service → product-service` (consulta de producto referenciado) es
una llamada síncrona REST, coherente con la decisión documentada de usar
"REST síncrono para queries, eventos asíncronos para side effects".

### Message Broker
Representado de forma genérica porque la elección final entre Kafka y
RabbitMQ está pendiente (ver `ADR-XXF1-eleccion-message-broker`). El
contrato de las relaciones de entrada/salida (publicación/consumo de
eventos) no cambia según cuál se elija, por lo que el diagrama es válido
independientemente del resultado de esa decisión.

### Notification Service
Único contenedor políglota del sistema (Node.js). Consume eventos del
Message Broker y los traduce en notificaciones multicanal. La
plantilla de notificación se selecciona según el campo `locale` que debe
viajar en el payload del evento (ver `docs/events/event-architecture.md`).

### Bases de datos (product-db, list-db, auth-db)
Cada una es una instancia PostgreSQL físicamente independiente,
coherente con Database-per-Service. `auth-db` es de uso interno de
Keycloak: el sistema ShoppingList no accede a ella directamente bajo
ninguna circunstancia.

---

## Decisiones abiertas reflejadas en este diagrama

Este diagrama refleja, a propósito, varias decisiones todavía no
cerradas formalmente en un ADR. Se documentan aquí para que el lector no
las confunda con decisiones firmes:

- **Elección de message broker** (Kafka vs. RabbitMQ) — pendiente en
  `ADR-XXF1-eleccion-message-broker`.
- **Gestión de transacciones distribuidas** entre `product-service` y
  `list-service` — actualmente sin Saga ni Resilience4j (deuda técnica
  aceptada en ADR-002). El diagrama no representa mecanismos de
  resiliencia ante fallos de red porque, a día de hoy, no existen.
- **Stack de observabilidad** — intencionadamente fuera de este
  diagrama (ver sección "Lo que este diagrama NO muestra").

---

## Relación con otros documentos

- `docs/architecture/c4-level1-context.md` — Vista de contexto (nivel
  superior a este documento).
- `docs/architecture/deployment-topology.md` — Topología de despliegue
  en AWS, incluyendo infraestructura de observabilidad (pendiente,
  Fase 6).
- `docs/events/event-architecture.md` — Detalle de los eventos
  publicados/consumidos a través del Message Broker.
- `<servicio>/docs/database-schema.md` — Esquema relacional detallado
  de cada base de datos representada aquí como caja única.
- `docs/adr/ADR-XXF1-eleccion-message-broker.md` — Decisión pendiente
  sobre Kafka vs. RabbitMQ.
