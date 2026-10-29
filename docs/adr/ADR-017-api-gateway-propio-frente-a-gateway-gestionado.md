# ADR-017: API Gateway propio frente a gateway gestionado

## Estado

Aceptado.

## Contexto

Cada servicio de dominio expone su propia API. Sin un borde delante, el
cliente tendría que conocer y alcanzar cada servicio por separado, y las
responsabilidades que no pertenecen a ningún dominio quedarían repartidas
o sin dueño. Un borde resuelve cinco responsabilidades concretas:

- **Punto único de entrada y ocultación de topología.** El cliente se
  dirige a un solo host. Los nombres, puertos y número de instancias de los
  servicios dejan de formar parte de la superficie pública y pueden
  cambiar sin tocar al cliente.
- **Generación centralizada de `correlationId` en el contexto donde
  nace.** La correlación debe originarse en el punto de entrada de la
  petición. El borde es el lugar más coherente y barato para hacerlo:
  todas las peticiones externas pasan por él y no se cuenta con que los
  servicios validen el formato de la cabecera, así que un valor ausente o
  mal formado llegaría hasta ellos sin garantía de correlación.
- **CORS centralizado.** La política de navegador se decide una vez y se
  aplica a todas las rutas. Dispersa en cada servicio, habría que
  repetirla y mantenerla sincronizada en cada cambio de origen o método.
- **Normalización del protocolo de errores en el borde.** Los fallos que
  produce el propio borde —ruta inexistente, servicio no disponible,
  timeout, límite de tasa— no nacen en el manejador de excepciones de un
  servicio. El borde los traduce al mismo contrato de error que el resto
  del sistema, para que el cliente no distinga un fallo de enrutado de
  uno de negocio.
- **Protección de los servicios de dominio.** El límite de tasa se aplica
  en el borde, antes de reenviar la petición, para que el abuso o una
  ráfaga de tráfico no llegue a los servicios. No es una responsabilidad
  que cada servicio deba resolver por su cuenta: la política es
  transversal y se aplica una sola vez, en el punto por el que pasan todas
  las peticiones externas. Su alcance y sus límites se concretan en la
  Decisión 3.

La decisión importa porque fija cinco cosas que no son detalles de
implementación: qué superficie se expone, cómo se traza una petición de
extremo a extremo, qué política de navegador se aplica, qué contrato de
error recibe el cliente y cómo se protege a los servicios del abuso.
Cambiar cualquiera de ellas después afecta a todos los consumidores a la
vez.

## Decisiones

### Decisión 1 — El borde es un componente propio del sistema

El sistema incorpora un API Gateway como componente propio, desplegado
junto al resto de servicios y bajo el mismo ciclo de vida. No es un
accesorio de infraestructura del proveedor ni una configuración externa
al repositorio: es la pieza donde viven las responsabilidades de borde
descritas, y se versiona, prueba y despliega como cualquier otro
componente.

Con el borde propio, esas responsabilidades tienen un único dueño. Sin
él, cada servicio tendría que resolver por su cuenta la correlación, la
política de CORS, el límite de tasa y la traducción de errores, y el
cliente asumiría el conocimiento de la topología. El borde concentra lo
transversal y deja a los servicios de dominio centrados en su lógica.

### Decisión 2 — Implementación con Spring Cloud Gateway autoalojado

El borde se implementa con Spring Cloud Gateway, en su variante Server
WebFlux, ejecutado como servicio propio sobre la misma plataforma que el
resto del backend. No es una elección provisional: migrar a la otra
variante implicaría reescribir los filtros del borde y rehacer su entorno
de pruebas, porque el código de filtros se escribe contra el modelo
reactivo de WebFlux.

La homogeneidad con el backend es de ecosistema, no literal: los servicios
de dominio son aplicaciones servlet sobre Spring MVC y el borde es
reactivo. Comparten lenguaje, herramienta de construcción y modelo de
configuración, pero no el mismo stack de ejecución. WebFlux es un stack
nuevo en el proyecto; el borde es el componente de menor riesgo para
introducirlo porque no contiene lógica de dominio que migrar ni romper:
su responsabilidad es enrutar y aplicar políticas transversales.

Frente al gateway gestionado del proveedor cloud, esta opción aporta las
capacidades que el proyecto necesita:

- **Control de filtros propios.** La correlación y las políticas de borde
  se implementan con el detalle que requiere el sistema. Un gateway
  gestionado impone su modelo de extensión y limita qué puede hacerse en
  el propio borde.
- **Preparación de la identidad en el borde.** El borde es el punto donde
  se integrará la identidad de la petición —sesión para el navegador o
  token para el cliente móvil, según el modelo que se adopte—. Esta
  decisión no fija ese modelo, pero reconoce que el borde necesita lógica
  propia y no solo enrutado.
- **Paridad local/producción.** El mismo Spring Cloud Gateway corre en
  Docker Compose y en producción. Las rutas y los filtros se prueban en
  local con el mismo motor que se despliega, sin traducir entre un
  gateway de desarrollo y otro de producción.

La decisión no atribuye al borde la autenticación ni la autorización:
fija dónde se integrarán cuando la fase de seguridad defina el modelo de
identidad.

### Decisión 3 — El límite de tasa es responsabilidad del borde

El borde aplica el límite de tasa como política transversal, no cada
servicio de dominio. En esta fase rige en memoria, por instancia y con
clave por la IP del socket. Es la opción compatible con el presupuesto $0:
no introduce un store compartido ni un componente adicional de
infraestructura.

La limitación es explícita: el cupo efectivo se multiplica con el número
de réplicas, porque cada instancia sostiene su propio cupo y no lo
comparte. Se acepta como limitación consciente; su resolución no forma
parte de esta decisión. El criterio para reevaluarla es doble: que haya un
proxy delante del borde —lo que cambia cuál es la IP de origen— o que el
despliegue tenga más de una réplica. El detalle mecanístico del límite
vive en su implementación, no en este documento.

### Decisión 4 — El borde normaliza el identificador de correlación

El borde honra la cabecera `X-Correlation-Id` solo cuando trae un único
valor y ese valor es un UUID canónico. Si la cabecera falta, si llega
repetida o si el valor no es un UUID canónico, genera uno nuevo.

La regla evita propagar identificadores no correlacionables y da a los
servicios una garantía que no obtienen por sí mismos: el identificador que
reciben siempre es válido. No se les pide validar la cabecera; la
normalización ocurre una vez, en el borde.

## Consecuencias

### Positivas

- **Desacoplamiento del cliente respecto a la topología.** El cliente
  conoce un solo host y no los servicios, sus puertos ni su número de
  instancias; la topología interna puede cambiar sin romper el contrato
  externo.
- **Punto único de evolución de las políticas de borde.** La correlación,
  la política de CORS, el límite de tasa y el contrato de error cambian en
  un solo sitio y aplican a todas las rutas.
- **Trazabilidad y contrato de error homogéneos.** Toda petición externa
  entra con un `correlationId` válido y recibe los errores en el mismo
  formato, incluidos los que genera el propio borde.
- **Paridad local/producción.** Las políticas del borde se prueban en local
  contra el mismo motor que se despliega en producción, sin doble
  operativa.

### Negativas / Trade-offs aceptados

- **SPOF (punto único de fallo) de acceso y de política.** Si el borde no
  está disponible, no entra ninguna petición; un error de rutas o de CORS
  afecta a todos los clientes a la vez, no a un servicio concreto.
- **Salto de red y latencia añadida.** Toda petición externa atraviesa un
  componente más. A la escala del sistema, esa latencia añadida no se espera
  perceptible; sus SLO se fijan en la fase de observabilidad cuando exista
  medición.
- **Coste operativo de un desplegable más.** El borde asume su propio
  ciclo de vida —imagen, configuración, salud, logs y actualizaciones de
  seguridad— además del de los servicios de dominio.
- **Límite de tasa en memoria multiplicado con las réplicas.** El cupo
  efectivo crece con el número de instancias porque cada instancia sostiene
  un cupo propio; se reevalúa cuando haya un proxy delante del borde o más
  de una réplica.
- **Restricción de red.** Los servicios de dominio dejan de ser
  alcanzables desde fuera del sistema: el único punto de entrada es el
  borde. En el despliegue compuesto solo se publica el 8080; los puertos
  de los servicios no son conectables desde el host.

### Alcance y control

La regla de alcance es estricta: en el borde solo viven políticas
transversales. No contiene lógica de dominio, ni agregación de respuestas,
ni acceso a datos. Si la agregación llegara a justificarse, su lugar sería
un BFF (Backend for Frontend), no el gateway.

El borde no conserva estado de negocio. Su único estado efímero es el
contador del límite de tasa, y por eso es replicable cuando la topología
de despliegue lo pida. Esa ausencia de estado mantiene bajo control el
riesgo de punto único: el borde puede crecer en instancias sin
coordinación entre ellas, al precio del cupo efectivo multiplicado que
declara la Decisión 3.

### Criterio de reevaluación

La decisión entre borde propio y gateway gestionado se reevalúa en la
topología de despliegue. La pregunta es doble: si el coste de cómputo de
operar el borde propio deja de compensar frente a lo que aporta —control
de filtros, paridad local/producción y la preparación de la identidad en
el borde—, y si la alternativa gestionada ofrecería en ese punto las
capacidades que motivaron ese control. Este documento no fija cifras.

## Alternativas consideradas

### Gateway gestionado del proveedor cloud (descartada)

Usar el gateway gestionado que ofrece el proveedor cloud para el
enrutado y las políticas de borde.

**Por qué se descartó:** su modelo de extensión limita los filtros
propios. La correlación, la preparación de identidad y la normalización
de errores obligarían a adaptar el sistema a las primitivas del
proveedor, y el ciclo de configuración y despliegue quedaría separado del
resto de servicios, rompiendo la paridad entre local y producción. El
coste operativo que se ahorra no compensa perder el control del borde en
la pieza que concentra las políticas transversales.

### Gateways autoalojados de otro ecosistema (descartada)

Usar un gateway autoalojado fuera del ecosistema Java/Spring —Kong,
Envoy, Traefik o NGINX— para el enrutado y las políticas de borde.

**Por qué se descartó:** la comparación es de ecosistema, no de producto.
Los filtros propios —correlación, identidad y contrato de error— se
escriben en Java/Spring y se comparten con los servicios; un gateway de
otra familia obligaría a mantener dos stacks, dos modelos de
configuración y dos operativas para el mismo borde. Mantener una sola
familia técnica reduce el conocimiento que hay que sostener.

### No tener borde (descartada)

Exponer cada servicio directamente al cliente, sin un componente de
borde.

**Por qué se descartó:** reparte las responsabilidades transversales
entre todos los servicios. El cliente asume la topología, de modo que
cualquier cambio de host, puerto o número de instancias se convierte en
un cambio de contrato; la correlación se genera en cada entrada y la
política de CORS y el contrato de error se duplican. Es exactamente la
situación que este ADR evita.

## Documentación relacionada

- **[ADR-003](ADR-003-service-discovery-nativo-y-descarte-de-eureka.md)** —
  el DNS interno como mecanismo de descubrimiento entre servicios, sin
  registry; el borde enruta hacia nombres de servicio y no sustituye esa
  localización interna.
- **[ADR-006](ADR-006-identificacion-propietario-sin-autenticacion.md)** —
  el `ownerId` como placeholder de propiedad en ausencia de identidad
  real; el borde queda preparado para incorporar la identidad cuando se
  defina.
- **`ADR-XXF3` — Keycloak como Identity Provider** — la decisión de
  identidad de la fase de seguridad, que superará el placeholder de
  ADR-006; en el borde se integrarán la sesión del navegador y el token
  del cliente móvil.
- **[`docs/logging.md`](../logging.md)** — la convención de
  `correlationId` y su propagación por cabecera; el borde es el punto
  donde la petición nace y donde se valida o genera el identificador.
- **[`docs/architecture/c4-level2-containers.md`](../architecture/c4-level2-containers.md)** —
  la posición del API Gateway en el mapa de contenedores y su relación
  con el resto del sistema.
- **Estrategia de observabilidad y trazabilidad distribuida** — se define
  en la fase de observabilidad; el borde es el punto donde nace el
  contexto de correlación que después se propaga por los servicios, y esta
  decisión no fija el estándar de trazas.
