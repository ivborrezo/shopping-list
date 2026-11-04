# ADR-018: Configuración externalizada nativa y descarte de Spring Cloud Config Server

## Estado

Aceptado.

## Contexto

Los servicios del sistema —`product-service`, `list-service`, `api-gateway`
y los contenedores previstos en la arquitectura objetivo, como
`auth-service` sobre Keycloak y `notification-service` en Node.js— necesitan
arrancar con parámetros que cambian según el entorno: desarrollo local,
despliegue compuesto con Docker Compose y producción cloud en AWS. URLs de
servicios, puertos, límites de tasa, orígenes CORS y credenciales de acceso
a recursos son configuración, no código. El principio III de *12-Factor App*
sitúa esos valores en el entorno, fuera del artefacto compilado, para que la
misma imagen pueda ejecutarse en cualquier entorno sin recompilarse.

El planteamiento inicial de la arquitectura contemplaba incorporar un
contenedor `config-service` basado en Spring Cloud Config Server: un
servidor centralizado que sirviera la configuración por HTTP a todos los
contenedores en el arranque, con el cliente correspondiente añadido al
classpath de cada servicio.

El estado real de los servicios construidos y las capacidades nativas de los
entornos de ejecución del proyecto resuelven ya ese mismo problema sin un
servidor intermedio. Los perfiles de Spring y las variables de entorno
cubren el desarrollo local y el despliegue compuesto; el orquestador cloud
inyecta la configuración en producción. La pregunta que queda es si un
servidor centralizado aporta algo que esos mecanismos no ofrezcan ya.

## Decisión

Se descarta `config-service` (Spring Cloud Config Server) y se adopta la
configuración externalizada nativa de cada entorno:

- **En desarrollo local (ejecución directa en el host):** perfiles de
  Spring —`application.yaml` con los valores base compartidos y
  placeholders `${VAR:default}`, más los overrides de
  `application-local.yaml`— junto con variables de entorno locales.
- **En el despliegue compuesto local (Docker Compose):** perfil `docker`
  (`application-docker.yaml`) combinado con las variables de entorno que
  `docker-compose.yml` inyecta desde `.env`. Su plantilla versionada es
  `.env.example`; `.env` permanece excluido de Git.
- **En producción cloud (AWS ECS):** inyección nativa de variables de
  entorno y secretos en las *Task Definitions* desde el almacén de la
  plataforma —AWS Systems Manager Parameter Store en su *Standard tier*,
  entre otras opciones—, con el mismo principio: la configuración entra
  desde fuera del contenedor.

En los tres entornos el artefacto compilado es el mismo; lo que cambia es el
perfil activo y el conjunto de valores que el entorno inyecta al arrancar.

### Por qué se descarta Spring Cloud Config Server

1. **Alineación cloud-native y *12-Factor App*.** Igual que el DNS interno
   de Docker Compose y de AWS Cloud Map hizo innecesario desplegar Netflix
   Eureka para localizar servicios (ADR-003), la inyección nativa de
   variables de entorno en Docker Compose y en ECS hace innecesario
   desplegar y mantener un servidor HTTP adicional cuya única función es
   entregar configuración.
2. **Coherencia con el monorepo y baja superposición entre servicios.** En
   una organización con decenas de repositorios independientes, un Config
   Server respaldado por un repositorio Git dedicado centraliza ficheros que
   de otro modo estarían dispersos. En este proyecto todo el código y los
   `application-*.yaml` viven versionados y auditados en un único monorepo
   (ADR-001), y cada servicio posee su propia base de datos y un conjunto de
   propiedades acotado a su dominio o al borde (ADR-002). El servidor
   central resolvería un problema de dispersión que aquí no existe.
3. **Arquitectura políglota y contenedores de terceros.** Los contenedores
   no basados en Spring —`notification-service` en Node.js, `auth-service`
   sobre Keycloak— no consumen Spring Cloud Config de forma nativa:
   exigirían clientes HTTP propios o una traducción manual del formato. Las
   variables de entorno inyectadas por el orquestador son un mecanismo
   estándar y agnóstico al lenguaje, idéntico en todos los contenedores
   (ADR-005 fija la convención de eventos como agnóstica al lenguaje,
   coherente con ese carácter políglota).
4. **Presupuesto $0 (Free Tier), simplicidad operativa y ausencia de
   necesidad de recarga en caliente.** Un Config Server añade un contenedor
   JVM permanente que consume memoria y CPU sin aportar funcionalidad de
   negocio, y un punto único de fallo —o un acoplamiento temporal— en la
   cadena de arranque de todos los servicios. Su ventaja diferencial, el
   refresco dinámico vía `/actuator/refresh` y Spring Cloud Bus sin
   reiniciar contenedores, no aporta valor en un sistema cuyos cambios de
   configuración viajan con despliegues inmutables de contenedores.

### Alcance diferido: gestión de configuración y secretos en AWS ECS

Este ADR fija el principio arquitectónico —configuración externalizada
nativa por entorno y descarte de Config Server—, pero no detalla cómo se
inyecta esa configuración en AWS ECS. La estructura y jerarquía de
parámetros en AWS Systems Manager (SSM) Parameter Store, los permisos IAM
del *execution role* de ECS y el mapeo en las *Task Definitions* pertenecen
a la fase de despliegue, y se documentarán en un ADR específico cuando esa
fase se aborde.

Dentro de esa fase, la comparación de costes orienta la elección. SSM
Parameter Store en su *Standard tier*, con parámetros `SecureString`
cifrados mediante la clave KMS gestionada por defecto `aws/ssm`, opera sin
coste fijo mensual de almacenamiento dentro de los límites estándar. AWS
Secrets Manager, en cambio, cobra un coste fijo mensual por secreto y por
llamadas a la API; solo se justificaría si algún secreto requiriera rotación
automática, capacidad que Parameter Store no ofrece por sí sola.

Esto no se considera deuda técnica: no existe un problema sin resolver,
sino una decisión de implementación que corresponde a la fase de despliegue y
se aborda en ella. Documentar el detalle antes de abordar esa fase sería
especular sobre una implementación que pertenece a ella, en contra del
criterio YAGNI ya establecido para la redacción de ADRs en este proyecto.

La decisión concreta se registra en
`ADR-XXXX-gestion-configuracion-y-secretos-ecs`.

## Consecuencias

### Positivas

- **Cero contenedores de infraestructura de configuración.** Nada que
  construir, desplegar, monitorizar ni mantener para entregar configuración
  dentro del presupuesto Free Tier.
- **Cero dependencias de cliente de configuración.** Ningún
  `spring-cloud-starter-config` en el classpath de los servicios Spring; el
  arranque es autónomo, sin dependencia de red hacia un servidor de
  configuración.
- **Mecanismo uniforme y agnóstico al lenguaje.** Variables de entorno del
  orquestador más los ficheros de perfil propios de cada tecnología, válido
  tanto para los servicios en Java/Spring como para Node.js y Keycloak.
- **Paridad conceptual entre local y producción.** El mismo principio
  —configuración inyectada desde fuera del contenedor— se mantiene en
  desarrollo local, en Docker Compose y en producción cloud, aunque el
  mecanismo concreto cambie.

### Negativas / limitaciones aceptadas

- **Sin recarga de configuración en caliente.** Cambiar un parámetro o rotar
  una credencial exige reiniciar o redesplegar el contenedor afectado
  (`docker compose up -d` en local o *rolling update* de tareas en ECS). Se
  acepta como trade-off consciente, alineado con el modelo de
  infraestructura inmutable basada en contenedores.
- **Duplicación puntual de variables transversales entre servicios.**
  Propiedades compartidas por varios contenedores —una URL del broker de
  mensajería o el *issuer URI* del proveedor de identidad— deben inyectarse
  en cada servicio afectado. Se mitiga definiéndolas en un único punto por
  entorno: `.env` y `docker-compose.yml` en local, parámetros compartidos en
  la plataforma cloud.
- **Disciplina sobre secretos en el monorepo.** Al convivir el código y los
  ficheros `application-*.yaml` en un único repositorio Git, los ficheros
  versionados nunca deben contener credenciales reales de entornos no
  locales, sino únicamente valores de desarrollo local o placeholders
  `${VAR}` resueltos por entorno. El valor por defecto de un placeholder de
  credencial es también texto versionado: solo puede ser un valor de
  desarrollo local asumible públicamente, nunca un secreto de un entorno real.
- **Alcance diferido a la fase de despliegue.** La configuración concreta de
  inyección en AWS ECS corresponde a
  `ADR-XXXX-gestion-configuracion-y-secretos-ecs`.

## Alternativas consideradas

- **Spring Cloud Config Server (`config-service` autoalojado):** descartado
  por los motivos detallados en "Por qué se descarta Spring Cloud Config
  Server".
- **Consul KV / HashiCorp Vault autoalojados:** ofrecen un almacén
  clave-valor distribuido y gestión avanzada de secretos, pero introducen el
  mismo sobrecoste operativo y de recursos que un Config Server —desplegar,
  mantener y asegurar un servidor adicional con su propio
  almacenamiento— sin una necesidad funcional que no cubran las variables
  de entorno en local y los servicios nativos de AWS dentro del Free Tier en
  la fase de despliegue.
- **Kubernetes ConfigMaps y Secrets:** resuelven este mismo problema de
  forma nativa en clústeres Kubernetes, pero quedan fuera de alcance porque
  la orquestación del proyecto se basa en Docker Compose en local y AWS ECS
  en producción cloud.

## Documentación relacionada

- **[ADR-001](ADR-001-monorepo-vs-polyrepo.md)** — el monorepo único donde
  viven versionados y auditados los `application-*.yaml` y las plantillas de
  entorno; la configuración base por perfil no necesita un repositorio
  dedicado.
- **[ADR-002](ADR-002-database-per-service-pattern.md)** — Database-per-Service:
  cada servicio posee su base de datos y, con ella, su configuración de
  persistencia, sin propiedades compartidas que justifiquen un servidor
  central.
- **[ADR-003](ADR-003-service-discovery-nativo-y-descarte-de-eureka.md)** —
  el DNS interno frente a Netflix Eureka: el precedente directo del mismo
  principio arquitectónico, sustituir un servidor propio por una capacidad
  nativa de la plataforma.
- **[ADR-017](ADR-017-api-gateway-propio-frente-a-gateway-gestionado.md)** —
  el borde propio, configurado por perfil como el resto de servicios, con
  políticas transversales que se resuelven en el componente y no en un
  servidor de configuración externo.
- **`ADR-XXXX-gestion-configuracion-y-secretos-ecs`** — la decisión de la
  fase de despliegue sobre la jerarquía de parámetros, los permisos IAM y el
  mapeo de configuración y secretos en las *Task Definitions* de ECS.
