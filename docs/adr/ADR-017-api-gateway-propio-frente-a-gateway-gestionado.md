# ADR-017: API Gateway propio frente a gateway gestionado

## Estado

Aceptado.

## Contexto

Cada servicio de dominio expone su propia API. Sin un borde delante, el
cliente tendría que conocer y alcanzar cada servicio por separado, y las
responsabilidades que no pertenecen a ningún dominio quedarían repartidas
o sin dueño. Un borde resuelve cuatro responsabilidades concretas:

- **Punto único de entrada y ocultación de topología.** El cliente se
  dirige a un solo host. Los nombres, puertos y número de instancias de los
  servicios dejan de formar parte de la superficie pública y pueden
  cambiar sin tocar al cliente.
- **Generación centralizada de `correlationId` en el contexto donde
  nace.** La correlación debe originarse en el punto de entrada de la
  petición. Cada servicio sabe reutilizar la cabecera o generar una
  nueva, pero con varios caminos de entrada la única forma de que una
  petición tenga un identificador común de extremo a extremo es que el
  borde lo genere antes de enrutar.
- **CORS centralizado.** La política de navegador se decide una vez y se
  aplica a todas las rutas. Dispersa en cada servicio, habría que
  repetirla y mantenerla sincronizada en cada cambio de origen o método.
- **Normalización del protocolo de errores en el borde.** Los fallos que
  produce el propio borde —ruta inexistente, servicio no disponible,
  timeout, rate limiting— no nacen en el manejador de excepciones de un
  servicio. El borde los traduce al mismo contrato de error que el resto
  del sistema, para que el cliente no distinga un fallo de enrutado de
  uno de negocio.

La decisión importa porque fija cuatro cosas que no son detalles de
implementación: qué superficie se expone, cómo se traza una petición de
extremo a extremo, qué política de navegador se aplica y qué contrato de
error recibe el cliente. Cambiar cualquiera de ellas después afecta a
todos los consumidores a la vez.

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
política de CORS y la traducción de errores, y el cliente asumiría el
conocimiento de la topología. El borde concentra lo transversal y deja a
los servicios de dominio centrados en su lógica.

### Decisión 2 — Implementación con Spring Cloud Gateway autoalojado

El borde se implementa con Spring Cloud Gateway, ejecutado como servicio
propio sobre la misma plataforma que el resto del backend.

Frente al gateway gestionado del proveedor cloud, esta opción aporta las
capacidades que el proyecto necesita:

- **Control de filtros propios.** La correlación y las políticas de borde
  se implementan con el detalle que requiere el sistema. Un gateway
  gestionado impone su modelo de extensión y limita qué puede hacerse en
  el propio borde.
- **Preparación de la identidad en el borde.** El borde es donde se
  resolverá la identidad de la petición: sesión para el navegador y token
  para el cliente móvil. Esta decisión no fija el modelo de identidad,
  pero sí reconoce que el borde necesita lógica propia y no solo
  enrutado.
- **Paridad local/producción.** El mismo Spring Cloud Gateway corre en
  Docker Compose y en producción. Las rutas y los filtros se prueban en
  local con el mismo motor que se despliega, sin traducir entre un
  gateway de desarrollo y otro de producción.

La decisión no atribuye al borde la autenticación ni la autorización:
fija dónde se integran cuando la fase de seguridad defina el modelo de
identidad.

## Consecuencias

### Positivas

- **Un único punto donde evolucionar las políticas de borde.** La
  correlación, la política de CORS y el contrato de error cambian en un
  solo sitio y aplican a todas las rutas.
- **Trazabilidad consistente.** Toda petición que entra por el borde
  lleva un `correlationId` generado en el origen, y los servicios lo
  reutilizan sin lógica adicional.
- **Stack y despliegue homogéneos.** El gateway comparte lenguaje,
  herramienta de construcción y modelo de configuración con el resto del
  backend.

### Negativas / Trade-offs aceptados

- **Un desplegable más que operar y actualizar.** El borde asume su
  propio ciclo de vida: imagen, configuración, salud, logs y
  actualizaciones de seguridad.
- **Políticas propias que hay que testear y mantener.** Los filtros de
  correlación, CORS y errores son código del proyecto y requieren sus
  pruebas, igual que cualquier otro componente.
- **El rate limiting en memoria no escala horizontalmente.** El límite se
  aplica por instancia y no se comparte entre réplicas: con varias
  instancias, cada una sostiene su propio cupo. Se acepta como limitación
  de esta decisión; su resolución corresponde a la deuda técnica del
  componente, no a este documento.

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
  donde la petición nace y donde se genera el identificador.
- **[`docs/architecture/c4-level2-containers.md`](../architecture/c4-level2-containers.md)** —
  la posición del API Gateway en el mapa de contenedores y su relación
  con el resto del sistema.
