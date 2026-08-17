# ADR-014: Estrategia de manejo de errores

## Estado

Redactado

## Contexto

Hoy `product-service` lanza `ResponseStatusException` e
`IllegalArgumentException` sin un modelo de error unificado: no hay sitio
para un código machine-readable, los mensajes no siguen un vocabulario
estable y el consumidor de la API —el frontend react-i18next y el futuro
API Gateway— no tiene un contrato de errores contraprestable. El
`ErrorResponse` custom (`{code, message}`) que documenta el
[`api-contract.yaml`](../product-service/docs/api-contract.yaml) tampoco
lo resuelve: el `code` no es un identificador de dominio estable y el
`message` se sirve localizado, así que el consumidor no tiene una señal
estable a la que acoplarse.

La decisión se toma tras la sesión de redefinición de las Ramas 7 y 8,
donde se cerraban las cuatro decisiones siguientes: el idioma de los
mensajes, el shape del error, el mecanismo para lanzarlo y el shape de la
validación de peticiones.

## Decisiones

### Decisión 1 — Errores en inglés con `status` y `code` estable, sin i18n en el backend

Los mensajes de error de la API son en inglés, sin localización en el
backend. La localización del texto que ve el usuario es responsabilidad
del frontend (react-i18next), que mapea el `code` a su propio
vocabulario.

Elijo el inglés porque el `message` tiene dos consumidores y ninguno es
el usuario final: el API Gateway lo trata como señal de máquina y los
logs de desarrollo se leen siempre en inglés. El frontend es el dueño del
texto de usuario, y para traducir solo necesita el `code`, no un mensaje
localizado que luego descartaría. Esto delimita el principio que ya fijó
[ADR-011](../adr/ADR-011-estrategia-de-internacionalizacion-y-fallback.md):
los datos se localizan, las señales de estado no. El vocabulario de error
es acotado y contraprestable; el dato del catálogo no. Es también la
convención de la industria: código + inglés.

### Decisión 2 — RFC 9457 `ProblemDetail` con extensión `code`

Cada error de la API se serializa según RFC 9457
(`application/problem+json`) con una extensión propia: `code`. El mapeo
de campos es el siguiente:

- `type` → `about:blank` (el default del RFC) salvo que el error tenga
  una URI de tipo propia.
- `title` → resumen estable en inglés, el mismo para todas las
  ocurrencias de ese error; lo aporta el enum de la Decisión 3.
- `status` → código HTTP del error.
- `detail` → mensaje específico en inglés; puede incluir el identificador
  del recurso implicado sin romper el contrato.
- `instance` → URI del recurso de la petición que produjo el error
  (opcional).
- `code` → identificador machine-readable del error; es el contrato entre
  el backend y el frontend, y la clave que el frontend usa para traducir.

Descarto el `ErrorResponse` custom (`{code, message}`) porque no seguía
ningún estándar y mezclaba el código con el mensaje sin estructura: era
un shape propio que cada consumidor tenía que entender por convención.
Descarto también el RFC 9457 puro, sin extensión: no tiene sitio para un
código de dominio estable, y sin él el frontend no puede traducir ni el
gateway enrutar con precisión.

### Decisión 3 — `BusinessException` + enum `ErrorCode` con un único `@RestControllerAdvice`

Un solo `@ControllerAdvice` (`GlobalExceptionHandler`) mapea todas las
excepciones a `ProblemDetail`. No habrá un handler por excepción ni
anotaciones dispersas en los controllers.

Los servicios lanzan `BusinessException`, construida con un valor del
enum `ErrorCode` —el catálogo, con su HTTP status y su título— y
opcionalmente un `detail` específico. El handler traduce el enum al
`ProblemDetail` de la Decisión 2.

Descarto mantener `ResponseStatusException`: no tiene sitio para el
`code` del contrato, y seguir usándola habría perpetuado el problema que
esta decisión resuelve. Descarto también una jerarquía tipada de
excepciones (una clase por error): hoy no hay catch por tipo ni datos
estructurados por error que la justifiquen, así que una clase por error
sería código sin valor añadido.

`ErrorCode` es el catálogo del contrato: contrastable con
[`api-contract.yaml`](../product-service/docs/api-contract.yaml), que
materializa el shape y la lista completa de códigos. El catálogo queda
así:

| Código | HTTP | Título |
|---|---|---|
| `CATEGORY_NOT_FOUND` | 404 | Category not found |
| `INVALID_CATEGORY` | 400 | Invalid category |
| `BASE_PRODUCT_NOT_FOUND` | 404 | Base product not found |
| `USER_PRODUCT_NOT_FOUND` | 404 | User product not found |
| `PRODUCT_NOT_FOUND` | 404 | Product not found |
| `DUPLICATE_PRODUCT_CODE` | 409 | Duplicate product code |
| `DUPLICATE_CATEGORY_CODE` | 409 | Duplicate category code |
| `UNSUPPORTED_LOCALE` | 400 | Unsupported locale |
| `NAME_REQUIRED` | 400 | Name is required |
| `DEFAULT_UNIT_REQUIRED` | 400 | Default unit is required |
| `CALORIES_PER_REQUIRED` | 400 | Calories per is required |
| `OWNER_MISMATCH` | 403 | Owner mismatch |
| `INVALID_PRODUCT_TYPE` | 400 | Invalid product type |
| `INVALID_BASE_PRODUCT` | 400 | Invalid base product |
| `VALIDATION_FAILED` | 400 | Validation failed |

Dos anotaciones sobre el catálogo:

- `INVALID_BASE_PRODUCT` se añadió al catálogo original de 13 valores
  durante la sesión de redefinición. El alta de un producto de usuario
  con `basedOnBaseId` apuntando a un producto base inexistente es un 400:
  el payload es semánticamente inválido, y la convención REST reserva el
  404 al recurso de la URL, no a las referencias del body. No existía un
  código 400 que representara este caso.
- `VALIDATION_FAILED` es un valor reservado de nivel superior: nunca lo
  lanza un servicio. Lo usa el `GlobalExceptionHandler` para el shape de
  validación de la Decisión 4. Está en el enum para que el catálogo
  completo sea contrastable con el contrato, pero no es un error de
  negocio lanzable.

El fallback de error genérico (excepción no controlada → 500) usa un
código `INTERNAL_ERROR` como constante del handler, fuera del enum: no
pertenece al catálogo de negocio y solo se documenta en el contrato, no
se enumera.

### Decisión 4 (A3) — Validación Bean Validation con errores por campo

Cuando falla la validación de la petición, el `ProblemDetail` lleva
`code: VALIDATION_FAILED` de nivel superior y un array `errors` con una
entrada por campo:

- `code` por campo → `FieldError.getCode()`: el nombre de la constraint,
  p. ej. `NotBlank`.
- `field` → nombre del campo que falla.
- `message` → `FieldError.getDefaultMessage()`: el mensaje de la
  constraint, en inglés por defecto, alineado con la Decisión 1.

Descarto el default de Spring para errores de validación: expone
`rejectedValue`, que no es estable ni seguro de mostrar al consumidor.
Descarto también el `invalid-params` del RFC 9457: es texto libre, sin un
código estable por campo que el frontend pueda traducir.

## Consecuencias

### Positivas

- Contrato de errores machine-readable y estable para frontend y gateway:
  el `code` es la clave del contrato y el `title` un resumen estable para
  logs y debugging.
- Catálogo único contraprestable con `api-contract.yaml`: el enum y el
  contrato no pueden divergir sin que se note.
- Un único punto de mapeo excepción→HTTP: toda la estrategia de errores
  vive en `GlobalExceptionHandler`, sin lógica dispersa en controllers.
- El `detail` puede llevar el identificador del recurso implicado sin
  romper el contrato: el frontend ignora el texto y el gateway lo usa
  como señal, ambos sin acoplarse a su redacción.
- El alcance es global: `list-service` replicará el patrón —enum
  `ErrorCode`, `BusinessException`, `GlobalExceptionHandler`— con su
  propio catálogo, sin reabrir el debate, mismo criterio que ADR-011.

### Negativas / Trade-offs aceptados

- **Mensajes en inglés sin localizar**: el frontend debe mapear el `code`
  a texto de usuario; si no lo hace, el usuario ve el mensaje en inglés.
  Es el coste de la Decisión 1, asumido.
- **El `code` por campo de validación es el nombre de la constraint** de
  Bean Validation, no un código de dominio: se depende del vocabulario de
  Hibernate Validator, que puede variar entre versiones del framework. Se
  acepta porque la alternativa —catálogo propio de códigos de campo—
  duplicaría el trabajo del framework sin valor real.
- **El `rejectedValue` se oculta deliberadamente** (decidido): no es
  estable ni seguro de exponer. Cuando el valor inválido aporte contexto,
  habrá que describirlo en el mensaje; es un coste consciente.

## Alternativas consideradas

### `ErrorResponse` custom (`{code, message}`) (descartada)

El shape que ya existía en el contrato.

**Por qué se descartó:** no seguía ningún estándar y mezclaba el código
con el mensaje sin estructura: el `code` no era un identificador de
dominio estable y el `message` se servía localizado, contradiciendo la
Decisión 1. Mantenerlo habría perpetuado un contrato que cada consumidor
debe entender por convención.

### RFC 9457 puro, sin extensión `code` (descartada)

Serializar los errores según RFC 9457 sin ninguna extensión.

**Por qué se descartó:** el RFC no tiene sitio para un código de dominio
estable; `title` es un resumen de texto, no un identificador. Sin `code`
el frontend no puede traducir ni el gateway enrutar con precisión, que es
exactamente el problema que esta estrategia resuelve.

### Mantener `ResponseStatusException` (descartada)

Seguir lanzando `ResponseStatusException` con el mensaje en el
constructor.

**Por qué se descartó:** no tiene sitio para el `code` del contrato; el
status y el texto viajan juntos sin estructura y el mensaje se repite en
cada llamada, sin un vocabulario estable que contrastar.

### Jerarquía tipada de excepciones (descartada)

Una clase de excepción por error, con sus campos tipados.

**Por qué se descartó:** no hay catch por tipo ni datos estructurados por
error hoy; una clase por error sería código sin valor añadido. El enum
`ErrorCode` aporta el mismo catálogo con un solo tipo.

### Default de Spring para validación (descartada)

Dejar que el mecanismo por defecto de Spring serialice los errores de
Bean Validation.

**Por qué se descartó:** expone `rejectedValue`, que no es estable ni
seguro de mostrar al consumidor, y no encaja en el shape de la Decisión 2
sin adaptación.

### `invalid-params` del RFC 9457 (descartada)

La extensión estándar del RFC para errores de validación.

**Por qué se descartó:** es texto libre, sin un código estable por campo
que el frontend pueda traducir. El array `errors` con `code`, `field` y
`message` de la Decisión 4 da esa estabilidad.

## Documentación relacionada

- **[`api-contract.yaml`](../product-service/docs/api-contract.yaml)** —
  el contrato que materializa el shape ProblemDetail y el catálogo de
  códigos de la Decisión 3.
- **[ADR-010](../adr/ADR-010-politica-de-testing-tdd-vs-test-after.md)** —
  la política de testing aplica a esta estrategia: los tests del contrato
  de error —TDD sobre `GlobalExceptionHandler` y sobre el shape de
  validación— son el primer ejercicio real de esta decisión.
- **[ADR-011](../adr/ADR-011-estrategia-de-internacionalizacion-y-fallback.md)** —
  fija que los datos se localizan (i18n Table) y que `list-service`
  replica los patrones sin re-decidir; la Decisión 1 de este ADR delimita
  que las señales de estado no se localizan.
- El alcance es global: `list-service` replicará `ErrorCode`,
  `BusinessException` y `GlobalExceptionHandler` con su propio catálogo,
  sin reabrir el debate, mismo criterio que ADR-011.