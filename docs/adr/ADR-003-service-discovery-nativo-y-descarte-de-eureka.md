# ADR-003: Service Discovery nativo (DNS) y descarte de Netflix Eureka

## Estado
Aceptado.

## Contexto

En una arquitectura de microservicios, los servicios necesitan
comunicarse entre sí de forma síncrona (REST) para resolver
operaciones que requieren datos de otro dominio. En este proyecto,
el caso concreto ya presente en Fase 1 es `list-service` necesitando
consultar a `product-service` (por ejemplo, para validar la existencia
de un producto base al añadirlo a una lista).

Esta comunicación es **interna entre servicios** (server-to-server) y
es conceptualmente distinta de la comunicación **externa hacia el
sistema** (cliente → API Gateway), que se resolverá en la Fase 3. El
API Gateway no sustituye la necesidad de que los servicios se
localicen entre sí; ambos mecanismos coexisten y resuelven tramos
distintos de la comunicación:

```
Cliente externo  ─────▶  API Gateway  ─────▶  product-service
(navegador, app)         (Fase 3)

list-service     ─────▶  product-service
(server-to-server, vía Service Discovery)
```

Para que un servicio pueda llamar a otro por nombre (en lugar de por
IP fija, que cambia en cada reinicio de contenedor), se necesita un
mecanismo de **Service Discovery**: una forma de traducir el nombre
lógico de un servicio (`product-service`) a su ubicación de red real
en cada momento.

El ecosistema Spring ofrece históricamente **Netflix Eureka** (Spring
Cloud Netflix) como solución estándar de Service Registry para este
problema: cada servicio se registra activamente contra un servidor
Eureka al arrancar, y los consumidores consultan ese registro para
descubrir instancias disponibles.

Sin embargo, tanto el entorno de desarrollo local (Docker Compose)
como el entorno cloud objetivo (AWS) ofrecen de forma nativa un
mecanismo equivalente basado en **DNS interno**, sin necesidad de
desplegar ni mantener un componente adicional:

- **Docker Compose**: todos los contenedores definidos en un mismo
  `docker-compose.yml` se conectan a una red privada compartida
  (en este proyecto, `shopping-list-net`). Docker provee un DNS
  interno automático que resuelve el nombre del servicio (tal como
  está definido en el `docker-compose.yml`) a la IP del contenedor
  correspondiente dentro de esa red. Esto ya está operativo desde
  el scaffolding inicial del proyecto.
- **AWS (ECS + Cloud Map)**: AWS Cloud Map ofrece el mismo principio
  para entornos distribuidos en múltiples máquinas — un namespace de
  DNS privado donde cada servicio desplegado en ECS se registra
  automáticamente y es resoluble por nombre desde otros servicios de
  la misma red (VPC).

## Decisión

Se descarta Netflix Eureka como mecanismo de Service Discovery para
este proyecto. En su lugar, se adopta **DNS interno** como mecanismo
nativo de descubrimiento de servicios:

- **En desarrollo local**: DNS interno de Docker Compose (ya operativo
  desde el scaffolding del monorepo).
- **En producción (AWS)**: AWS Cloud Map, integrado con ECS.

Las llamadas entre servicios se realizan directamente contra el
nombre de host del servicio destino (ej: `http://product-service:8081`
en lugar de una IP), usando los clientes HTTP estándar de Spring
(`RestTemplate` o `WebClient`), sin ninguna librería cliente de
discovery añadida al classpath.

### Por qué se descarta Eureka

1. **Componente operativo adicional sin beneficio neto en este
   contexto.** Eureka requiere desplegar y mantener un servidor de
   registro propio (`eureka-server`), que es a su vez un punto de
   fallo a vigilar y un servicio más que escalar, securizar y
   observar. El DNS interno no añade ningún componente: es una
   capacidad ya incluida en la infraestructura que el proyecto usa
   de todos modos (Docker Compose, ECS).
2. **Acoplamiento de cada microservicio a una librería de
   infraestructura.** Usar Eureka implica añadir
   `spring-cloud-starter-netflix-eureka-client` a cada servicio.
   Esto añade una fricción crítica al integrar componentes fuera
   del ecosistema Java, como nuestro Notification Service en
   Node.js (Fase 5), el cual tendría que emular dicho cliente de
   registro. Con DNS, el sistema es 100% agnóstico al lenguaje.
3. **Estado del proyecto.** Spring Cloud Netflix (incluyendo Eureka)
   se encuentra en modo de mantenimiento dentro del ecosistema
   Spring Cloud, sin desarrollo activo de nuevas funcionalidades.
   No es la recomendación vigente para proyectos nuevos, lo cual
   refuerza —pero no es la razón principal de— la decisión anterior.

### Alcance diferido: configuración de AWS Cloud Map

Este ADR fija el **principio** (DNS interno como mecanismo de
discovery, tanto en local como en AWS) pero no detalla **cómo** se
configurará Cloud Map con ECS (namespaces, integración con Service
Connect/Service Discovery de ECS, TTLs de DNS, etc.). Esa
configuración concreta pertenece a la Fase 3 (infraestructura de
plataforma) y se documentará en un ADR específico cuando esa fase
se aborde de forma irreversible.

Esto no se considera deuda técnica: no existe hoy un problema sin
resolver, sino una decisión de implementación que aún no aplica,
porque el sistema no se despliega todavía en AWS. Documentar el
detalle ahora sería especular sobre una implementación no iniciada,
en contra del criterio ya establecido para la redacción de ADRs en
este proyecto (principio YAGNI).

ADR futuro provisional: `ADR-XXXX-configuracion-cloud-map-ecs`.

## Consecuencias

### Positivas
- Cero componentes de infraestructura adicionales que desplegar,
  versionar o monitorizar.
- Cero dependencias de librería de discovery en el classpath de los
  microservicios; el código de llamada entre servicios es HTTP
  estándar.
- Coherente con el principio de maximizar Free Tier: no hay un
  `eureka-server` consumiendo recursos de cómputo.
- El mismo principio (DNS) se mantiene conceptualmente en local y en
  AWS, aunque el mecanismo concreto cambie (Docker DNS → Cloud Map).
  Esto simplifica el razonamiento sobre la arquitectura: no hay un
  comportamiento de discovery exclusivo de un entorno que deba
  "traducirse" al pasar a otro.

### Negativas / limitaciones aceptadas
- El DNS interno no ofrece por sí mismo balanceo de carga del lado
  cliente con políticas avanzadas (ej: round-robin ponderado,
  zone-aware routing) como sí permite un Service Registry como
  Eureka combinado con Spring Cloud LoadBalancer. Para el alcance
  actual del proyecto (pocas instancias por servicio, sin
  multi-zona) esto no supone una limitación práctica.
- La resolución de salud de instancias (saber si una instancia
  registrada está realmente lista para recibir tráfico, no solo
  "viva") depende del mecanismo de health check del orquestador
  (Docker healthcheck / ECS health check) en lugar de los heartbeats
  propios de Eureka. Se considera suficiente para el alcance del
  proyecto, pero es una responsabilidad que pasa del framework
  (Spring Cloud) a la infraestructura (Docker/ECS).
- Queda pendiente, como trabajo futuro y no como deuda técnica, la
  decisión de configuración concreta de AWS Cloud Map (ver sección
  "Alcance diferido" arriba).

## Alternativas consideradas

- **Netflix Eureka (Spring Cloud Netflix)**: descartado por las
  razones detalladas en "Por qué se descarta Eureka".
- **Consul / HashiCorp Consul**: ofrece Service Discovery más rico
  (incluye KV store, health checking avanzado) pero introduce el
  mismo problema de fondo que Eureka — un componente adicional que
  desplegar y mantener — sin que el proyecto tenga un requisito que
  justifique esa complejidad añadida frente al DNS nativo ya
  disponible.
- **Kubernetes Service Discovery**: el propio Kubernetes resuelve
  este problema de forma nativa (de manera conceptualmente similar
  a Docker Compose/Cloud Map), pero queda fuera de alcance al no
  usarse Kubernetes como orquestador en este proyecto (se usa
  Docker Compose en local y ECS en AWS).
