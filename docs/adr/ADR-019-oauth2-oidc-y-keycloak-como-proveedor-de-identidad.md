# ADR-019: OAuth2/OIDC y Keycloak como proveedor de identidad

## Estado

Aceptado.

## Contexto

El sistema funciona desde el MVP con un placeholder de propiedad: el
`ownerId` viaja como dato de entrada en las peticiones y los servicios lo
aceptan sin comprobar ninguna identidad ([ADR-006](ADR-006-identificacion-propietario-sin-autenticacion.md)).
Ese placeholder permitió construir el flujo completo de extremo a extremo,
pero deja abierta la suplantación: cualquiera puede operar sobre los
recursos de otro propietario con solo conocer o adivinar su identificador.

El modelo de dominio ya está asentado —productos base y de usuario
([ADR-012](ADR-012-modelo-productos-base-vs-usuario.md)) e identificadores
públicos UUID ([ADR-016](ADR-016-estrategia-de-identificadores-publicos.md))—
y la pieza que falta para que la propiedad signifique algo es una identidad
verificada. La decisión debe cubrir varios frentes a la vez:

- **Un estándar abierto de identidad.** OAuth2 con OpenID Connect (OIDC)
  es el mecanismo con el que un proveedor externo emite un token firmado
  que los servicios pueden verificar sin consultar una sesión central.
- **Dos tipos de cliente.** El navegador no puede custodiar tokens de
  acceso —guardarlos en `localStorage` o `sessionStorage` los expone a
  XSS—, mientras que scripts, clientes API y suites de prueba se autentican
  de forma *stateless* con una cabecera `Authorization: Bearer <jwt>`.
- **Una frontera de confianza real.** La red interna no es una garantía de
  identidad: aceptar cabeceras planas inyectadas por el borde sin
  validación criptográfica en destino deja la autorización a merced de
  cualquier servicio alcanzable.
- **Roles de negocio.** El catálogo global se administra con un rol
  `ADMIN` y las listas y productos personales pertenecen a un usuario
  `USER`; ambos roles deben viajar en el token y no depender de datos de la
  petición.
- **Portabilidad y presupuesto.** El desarrollo local y el despliegue
  compuesto usan Docker Compose, mientras que la producción cloud tiene que
  caber en el presupuesto $0 / Free Tier sin sostener un contenedor pesado
  de Keycloak y una base de datos dedicada. La configuración entra desde
  fuera del artefacto ([ADR-018](ADR-018-configuracion-externalizada-nativa-y-descarte-de-config-server.md)),
  de modo que el mismo binario pueda apuntar a proveedores distintos.

En local convive además una restricción concreta de red: el navegador del
desarrollador se ejecuta en la máquina host y redirige a `localhost`,
mientras que los servicios corren dentro de la red bridge de Docker Compose.
Un `issuer` que solo resuelva dentro de la red rompe la validación del
emisor en el navegador; uno que solo resuelva en el host no sirve para las
llamadas de *backchannel*.

## Decisiones

### Decisión 1 — Estándar OAuth2/OIDC con Keycloak en local y portabilidad a un IdP gestionado en cloud

Se adopta OAuth2/OIDC como modelo de identidad y Keycloak autoalojado como
proveedor en desarrollo local y en el despliegue compuesto, con un
contenedor `auth-service` y su base de datos `auth-db` exclusiva
(Database-per-Service, [ADR-002](ADR-002-database-per-service-pattern.md)).
El realm del sistema es `shopping-list`.

El código se mantiene desacoplado del proveedor concreto:

- **Spring Security OAuth2 estándar**, sin librerías ni APIs propietarias
  de Keycloak (`org.keycloak.*`) y sin leer su estructura anidada
  `realm_access.roles`.
- **Roles de negocio en un claim plano configurable** (`roles`), de forma
  que el mismo código funcione con cualquier proveedor OIDC que pueda
  emitir un claim de roles.
- **Configuración externalizada** ([ADR-018](ADR-018-configuracion-externalizada-nativa-y-descarte-de-config-server.md)):
  `issuer`, credenciales de cliente, scopes y nombre del claim de roles se
  inyectan por entorno, sin cambios en el artefacto.

El `issuer` canónico en local y en Docker Compose es
`http://localhost:8083/realms/shopping-list`, que el navegador del host
puede resolver y validar. Las llamadas de *backchannel* entre contenedores
—intercambio de código por tokens y consulta de JWKS— usan el DNS interno
(`http://auth-service:8080/...`). Se descarta fijar un único `issuer`
interno exigiendo editar `/etc/hosts`, y se descarta desactivar la
validación del claim `iss`.

Los servicios de dominio no derivan la ubicación de las claves del
descubrimiento OIDC sobre el `issuer` canónico: obtienen el JWKS de la URL
interna de `auth-service` y mantienen la validación del claim `iss` contra
el `issuer` canónico externo/local. Ambas URLs —la canónica del emisor y la
interna del JWKS— se externalizan
([ADR-018](ADR-018-configuracion-externalizada-nativa-y-descarte-de-config-server.md)),
de modo que el descubrimiento no consulta `localhost` desde dentro de los
contenedores, donde ese nombre no resuelve.

En producción cloud se usará un proveedor OIDC gestionado con capa
gratuita, como AWS Cognito o Auth0/Okta, sin recompilar los servicios y sin
mantener el contenedor de Keycloak ni su base de datos. La elección
concreta del proveedor se registra en
`ADR-XXXX-idp-gestionado-en-produccion`.

### Decisión 2 — Modelo de seguridad dual en el borde: OAuth2 BFF de seguridad y Resource Server `Bearer`

`api-gateway` ([ADR-017](ADR-017-api-gateway-propio-frente-a-gateway-gestionado.md))
implementa dos cadenas `SecurityWebFilterChain` ordenadas, una por canal:

1. **Canal *stateless* (`@Order(1)`).** Atiende las peticiones que llegan
   con `Authorization: Bearer <jwt>`. Valida el token sin crear ni leer
   sesión y sin exigir CSRF, y lo reenvía al servicio de destino. Es el
   canal de scripts, clientes API y suites de prueba.
2. **Canal de sesión OIDC / OAuth2 BFF de seguridad (`@Order(2)`).**
   Gestiona el flujo Authorization Code
   (`/oauth2/authorization/{registrationId}` y
   `/login/oauth2/code/{registrationId}`) y `/logout`, y almacena los
   tokens OIDC en memoria con `InMemoryWebSessionStore`, acotado por un TTL
   de inactividad y un límite máximo de sesiones (`maxSessions`). El
   navegador solo recibe una cookie `SESSION` con `HttpOnly=true` y
   `SameSite=Lax`; las peticiones mutadoras autenticadas por sesión exigen
   protección CSRF, y el filtro `TokenRelay` inyecta
   `Authorization: Bearer <access_token>` en las llamadas a los servicios
   internos.

Este patrón actúa exclusivamente como BFF **de seguridad**: mantiene
intacta la regla de borde de [ADR-017](ADR-017-api-gateway-propio-frente-a-gateway-gestionado.md)
de no introducir lógica de dominio ni agregación de respuestas en el
gateway. El estado efímero del borde —el contador del límite de tasa y el
almacén acotado de sesiones— no es estado de negocio y permite seguir
operando con instancias independientes.

Las dos cadenas se declaran separadas porque los requisitos de cada canal
son incompatibles: el canal `Bearer` no puede tolerar una redirección al
proveedor de identidad ni una sesión implícita, y el canal de navegador no
puede delegar la custodia del token al JavaScript de la página. Un almacén
de sesiones externo (Redis o ElastiCache) se descarta: introduce un coste
fijo mensual y una dependencia operativa que la topología de instancia
única actual no necesita.

### Decisión 3 — Defensa en profundidad como OAuth2 Resource Server en los servicios de dominio y retirada del placeholder `ownerId`

`product-service` y `list-service` se configuran también como OAuth2
Resource Servers *stateless*. Cada petición se verifica criptográficamente
en destino: firma mediante JWKS, expiración (`exp`), emisor (`iss`) y que
el claim de sujeto (`sub` o el claim configurado) sea un `UUID` válido
([ADR-016](ADR-016-estrategia-de-identificadores-publicos.md)). Las
autoridades `ROLE_USER` y `ROLE_ADMIN` se mapean desde el claim de roles
configurado.

La consecuencia directa es la retirada del placeholder de
[ADR-006](ADR-006-identificacion-propietario-sin-autenticacion.md):

- `ownerId` desaparece de **todos** los cuerpos de petición y parámetros de
  query. El propietario se deriva exclusivamente del token verificado.
- `ownerId` se conserva como **atributo de lectura** en las respuestas
  (`UserProduct`, `ShoppingList`, `ShoppingListSummary`), porque describe
  la propiedad del recurso y el cliente lo necesita para presentarlo.
- Las escrituras del catálogo global (`POST /categories` y
  `POST/PATCH/DELETE /base-products`) exigen `ROLE_ADMIN`.
- Todas las operaciones sobre listas y productos de usuario comprueban la
  propiedad frente al `UUID` del token, incluidas las lecturas
  `GET /lists/{id}` y `GET /user-products/{id}` y el alta de favorito
  `POST /user-products/{id}/favorite` con `productType=USER`.
- `list-service` propaga `Authorization: Bearer <jwt>` en sus llamadas
  síncronas a `product-service`.

No se aceptan cabeceras planas no firmadas como `X-User-Id` o
`X-User-Roles`: la validación en cada servicio es la que convierte la red
interna en una frontera de confianza solo parcial y evita que un salto
directo a un servicio comprometa la autorización.

### Decisión 4 — Modelo de roles, propagación interna y contrato de errores de seguridad

Los roles de negocio son `USER` y `ADMIN`. Se proyectan en el claim plano
configurable `roles` y se traducen en runtime a las autoridades
`ROLE_USER` y `ROLE_ADMIN`. El rol no viaja en la ruta, en el cuerpo ni en
una cabecera propia: se lee del token verificado.

El contrato de errores de seguridad se estandariza sobre RFC 9457
`ProblemDetail` con la extensión `code` de
[ADR-014](ADR-014-estrategia-de-manejo-de-errores.md):

- Las redirecciones `302` se acotan al inicio del login interactivo
  (`GET /oauth2/authorization/{registrationId}`). Cualquier fallo de
  autenticación en una ruta protegida o en el callback OIDC responde
  `401 Unauthorized` con `code: UNAUTHENTICATED`, nunca con una redirección.
- Las denegaciones por rol insuficiente o por CSRF inválido responden
  `403 Forbidden` con `code: ACCESS_DENIED`.
- Las violaciones de propiedad de recursos de dominio responden
  `403 Forbidden` con `code: OWNER_MISMATCH`. El código cubre las
  violaciones directas: el recurso direccionado en la ruta (`ShoppingList`,
  ítem o `UserProduct`, también en `POST /user-products/{id}/favorite`)
  pertenece a otro usuario.
- Cuando `list-service` detecta que un `UserProduct` referenciado al añadir
  un ítem pertenece a otro usuario o no existe, responde `400 Bad Request`
  con `code: INVALID_PRODUCT_REFERENCE`. Ahí la referencia es indirecta —no
  el recurso direccionado— y el error de entrada se mantiene a propósito:
  distinguir la referencia ajena de la inexistente revelaría la existencia
  de identificadores ajenos y abriría la enumeración del catálogo de otros
  usuarios.
- No se exponen códigos internos de validación de tokens
  (`EXPIRED_TOKEN`, `INVALID_SIGNATURE`, etc.): el cliente solo necesita
  saber que la petición no está autenticada.

Por todo lo anterior, este ADR supera formalmente a
[ADR-006](ADR-006-identificacion-propietario-sin-autenticacion.md), que
queda como registro histórico del placeholder `ownerId`.

## Consecuencias

### Positivas

- **Identidad verificada y propiedad real.** La autorización deja de
  depender de un dato de la petición: el propietario es el sujeto del token
  y la suplantación por conocimiento de un `UUID` deja de ser posible.
- **Defensa en profundidad.** El borde valida la sesión o el token, y cada
  servicio vuelve a validar la firma y los permisos. Un fallo de
  configuración en un salto no abre el acceso a los datos.
- **Portabilidad de proveedor.** El código usa Spring Security OAuth2
  estándar y configuración externalizada; cambiar de Keycloak a un
  proveedor OIDC gestionado no requiere recompilar ni reescribir filtros.
- **Sesión de navegador protegida.** El token no llega nunca al
  JavaScript de la página: la cookie es `HttpOnly`, la sesión caduca por
  inactividad y está acotada en número, y las mutaciones exigen CSRF.
- **Contrato de errores coherente.** Los fallos de autenticación y
  autorización usan el mismo `ProblemDetail` y el mismo catálogo de
  códigos que el resto del sistema.

### Negativas / Trade-offs aceptados

- **Un componente de identidad que mantener en local.** `auth-service` y
  `auth-db` añaden dos contenedores al despliegue compuesto y su propio
  ciclo de vida, aunque en producción cloud se sustituyan por un servicio
  gestionado.
- **Estado de sesión en memoria del borde.** El almacén de sesiones vive
  por instancia y no se comparte; con varias réplicas, una sesión creada en
  una instancia no atiende una petición enrutada a otra. Se acepta mientras
  la topología sea de instancia única, con el mismo criterio que el límite
  de tasa de [ADR-017](ADR-017-api-gateway-propio-frente-a-gateway-gestionado.md).
- **Doble validación del token.** El borde y los servicios validan la firma
  y los claims; es trabajo redundante por petición que se asume como precio
  de la defensa en profundidad.
- **Datos del placeholder sin migración.** Los recursos creados con
  `ownerId` arbitrario no se reasocian a usuarios reales: no existe una
  correspondencia significativa que preservar y se descartan, como ya
  preveía [ADR-006](ADR-006-identificacion-propietario-sin-autenticacion.md).
- **Acoplamiento al contrato OIDC del proveedor.** Aunque el código sea
  estándar, el claim de roles y la configuración del cliente deben existir
  en cada proveedor; es configuración por entorno, no lógica de negocio.

## Alternativas consideradas

### Mantener el placeholder `ownerId` sin autenticación (descartada)

Seguir aceptando el propietario como dato de la petición.

**Por qué se descartó:** es exactamente el problema que este ADR resuelve.
El placeholder fue válido para construir el flujo sin bloquear el
desarrollo, pero no protege datos ni permite distinguir un propietario
legítimo de un suplantador.

### Acoplar los servicios a APIs propietarias de Keycloak (descartada)

Leer roles desde `realm_access.roles` o usar clases de `org.keycloak.*`.

**Por qué se descartó:** ata el código a un proveedor concreto y hace que
la sustitución por un IdP gestionado en producción deje de ser un cambio
de configuración. El claim plano configurable da la misma información sin
acoplamiento.

### `issuer` único con `/etc/hosts` o validación de `iss` desactivada (descartada)

Fijar `http://auth-service:8080/realms/shopping-list` en todos los entornos
y resolverlo en el host, o aceptar tokens sin comprobar el emisor.

**Por qué se descartó:** la primera opción exige un paso manual en cada
máquina de desarrollo y rompe la paridad local/producción; la segunda
elimina una validación que impide aceptar tokens de otro emisor. El
`issuer` canónico en `localhost` con *backchannel* por DNS interno cubre
ambos lados sin intervención manual.

### Solo validación `Bearer` en el borde con tokens en el navegador (descartada)

Delegar la custodia del token al JavaScript de la aplicación y validar
únicamente la cabecera `Authorization`.

**Por qué se descartó:** expone el token a XSS y obliga al cliente web a
gestionar refresco, expiración y cierre de sesión. El canal de sesión con
cookie `HttpOnly` mantiene el token fuera del alcance del JavaScript.

### Validar el JWT solo en el gateway y propagar cabeceras planas (descartada)

Aceptar en los servicios internos cabeceras como `X-User-Id` o
`X-User-Roles` inyectadas por el borde, sin validación criptográfica en
destino.

**Por qué se descartó:** convierte la red interna en la única defensa. Un
servicio alcanzable directamente, o un error de enrutado, permitiría
falsificar la identidad con una cabecera. La validación en cada servicio es
la defensa en profundidad que este ADR fija.

### Almacén de sesiones externo para el borde (descartada)

Usar Redis o ElastiCache para compartir las sesiones OAuth2 entre
instancias.

**Por qué se descartó:** añade un coste fijo mensual y un componente
operativo más para un problema —sesiones compartidas entre réplicas— que la
topología actual de instancia única no tiene. Se reevalúa si el despliegue
pasa a varias réplicas.

### Combinar `oauth2Login` y `oauth2ResourceServer` en una sola cadena (descartada)

Una única `SecurityWebFilterChain` que atienda ambos tipos de petición.

**Por qué se descartó:** mezcla dos políticas incompatibles —sesión y
CSRF frente a *stateless* sin sesión— y hace impredecible qué regla se
aplica a cada ruta. Las cadenas ordenadas separan el canal por el que llega
la credencial.

### Eliminar `ownerId` también de las respuestas (descartada)

Dejar de exponer el propietario en `UserProduct`, `ShoppingList` y
`ShoppingListSummary`.

**Por qué se descartó:** la propiedad es un dato legítimo del recurso y el
cliente lo necesita para presentarlo. Retirarlo del *input* elimina la
suplantación; retirarlo del *output* solo empobrece el contrato.

## Documentación relacionada

- **[ADR-002](ADR-002-database-per-service-pattern.md)** — `auth-db` es una
  base de datos exclusiva del proveedor de identidad; el sistema no accede
  a ella directamente.
- **[ADR-006](ADR-006-identificacion-propietario-sin-autenticacion.md)** —
  el placeholder `ownerId` que este ADR supera y deja como registro
  histórico de por qué el sistema funcionó sin autenticación.
- **[ADR-012](ADR-012-modelo-productos-base-vs-usuario.md)** — el modelo de
  productos base y de usuario sobre el que se aplican la propiedad y el
  gobierno del catálogo por rol.
- **[ADR-014](ADR-014-estrategia-de-manejo-de-errores.md)** — el contrato
  `ProblemDetail` RFC 9457 con extensión `code` que recogen los errores de
  seguridad.
- **[ADR-016](ADR-016-estrategia-de-identificadores-publicos.md)** — la
  identidad de usuario como `UUID` proveniente del proveedor de identidad.
- **[ADR-017](ADR-017-api-gateway-propio-frente-a-gateway-gestionado.md)** —
  el borde propio sobre el que se integran la sesión y la validación del
  token, sin lógica de dominio ni agregación de respuestas.
- **[ADR-018](ADR-018-configuracion-externalizada-nativa-y-descarte-de-config-server.md)** —
  la configuración externalizada que permite apuntar al proveedor OIDC de
  cada entorno sin recompilar.
- **`ADR-XXXX-idp-gestionado-en-produccion`** — la elección del proveedor
  OIDC gestionado concreto para el despliegue en producción cloud.
