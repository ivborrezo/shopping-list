# ADR-011: Estrategia de internacionalización y fallback

## Estado

Aceptado

## Contexto

ShoppingList es un sistema multiidioma. Las entidades gestionadas por
el sistema (categorías en `product-service`, futuras entidades en
`list-service`) tienen nombres que deben servirse en español, inglés
y euskera, según lo solicite el cliente vía la cabecera
`Accept-Language`. El endpoint `GET /categories` existente desde la
Rama 2 no devolvía nombres localizados. La Rama 3 añade el soporte
completo mediante el patrón i18n Table y requiere decidir cuatro
aspectos del mecanismo de internacionalización: la estrategia de
fallback ante traducciones ausentes, el mecanismo de resolución del
`Locale` desde la petición HTTP, dónde se ejecuta la consulta de
traducciones, y cómo se valida el locale en las peticiones de
creación.

## Decisión

### D1 — Estrategia de fallback: exacto → inglés → primer disponible

Algoritmo implementado en
[`CategoryService.resolveName()`](../../product-service/src/main/java/dev/ivborrezo/shoppinglist/product/service/category/service/CategoryService.java):
se busca la traducción cuyo locale coincida exactamente con el
`Locale` resuelto de la petición. Si no existe, se aplica fallback a
inglés (`en`). Si inglés tampoco existe, se devuelve la primera
traducción disponible (cualquier idioma). El último paso es un
*safety net* teórico que solo se activaría si una categoría carece de
traducción en inglés — escenario que el seed y la validación de
`create()` previenen.

**Alternativa descartada: devolver `null` o lanzar error.** Peor
experiencia de usuario y rompe el principio de que toda categoría
tiene al menos un nombre visible.

**Alternativa descartada: exacto → primer disponible sin preferencia
por inglés.** El inglés es la *lingua franca* del equipo y del
dominio; debe ser el default.

### D2 — AcceptHeaderLocaleResolver sobre parsing manual

Spring ya dispone de `AcceptHeaderLocaleResolver`, que parsea la
cabecera `Accept-Language`, la contrasta contra una lista de locales
soportados configurable y aplica fallback a un default cuando el
locale solicitado no está soportado o la cabecera está ausente o
malformada. Reimplementar este comportamiento en el controller sería
duplicar funcionalidad probada del framework.

La configuración vive en
[`LocaleConfig`](../../product-service/src/main/java/dev/ivborrezo/shoppinglist/product/service/config/LocaleConfig.java):
`setSupportedLocales(es, en, eu)`, `setDefaultLocale(en)`. El
controller recibe el `Locale` ya resuelto como parámetro de método,
sin lógica manual.

**Alternativa descartada: `LocaleContextResolver` con cookie/sesión.**
Añade estado innecesario en una API REST sin sesión de usuario.

**Alternativa descartada: `@RequestHeader("Accept-Language")` +
parsing manual.** Más código que mantener, más superficie de bugs para
casos límite (ausente, malformado, no soportado).

### D3 — Resolución del nombre en capa de servicio (Java), no en JPQL

`findByIsActiveTrue()` carga las `Category` con su
`Set<CategoryTranslation>` vía lazy loading de Hibernate dentro de
`@Transactional(readOnly = true)`. La iteración y el algoritmo de
fallback se ejecutan en Java (`resolveName()`). Con 10 categorías y 30
traducciones (3 idiomas × 10 categorías), esto produce 11 queries
(1 + 10) — coste despreciable.

**Alternativa descartada: `JOIN FETCH` en el repository.** Reduce a 1
query pero añade complejidad al repository.

**Alternativa descartada: resolver en SQL con `COALESCE`.** La lógica
de fallback en SQL es frágil e ilegible y acopla el DTO al
repository.

Si el volumen de categorías o idiomas creciera hasta que el N+1 fuera
medible, se reevaluaría con `@EntityGraph` o `JOIN FETCH`; hoy sería
optimización prematura sin datos de carga real.

### D4 — Validación de locale en dos capas: Bean Validation + servicio

Bean Validation en el DTO
([`CreateCategoryRequest`](../../product-service/src/main/java/dev/ivborrezo/shoppinglist/product/service/category/dto/CreateCategoryRequest.java),
`@Size(min = 2, max = 5)`) garantiza que el string tiene formato de
locale (longitud plausible) y produce un 400 Bad Request temprano para
valores claramente inválidos. El servicio
(`validateSupportedLocales()`) comprueba que cada locale esté en la
lista de soportados (`es`, `en`, `eu`). Son dos validaciones de
naturaleza distinta (formato vs. regla de negocio) en dos capas
distintas (presentación vs. dominio).

**Alternativa descartada: validator custom de Jakarta
(`@SupportedLocale`).** Acopla la lista de soportados al DTO y mezcla
responsabilidades de infraestructura con la capa de presentación.

**Alternativa descartada: solo validación en servicio.** Pierde el 400
temprano para locale malformado; la petición llegaría al servicio y la
excepción se traduciría a 500 (hasta Rama 6).

## Consecuencias

### Positivas

- El mecanismo es simple, testeable y delegado al framework donde
  corresponde.
- `list-service` puede replicar `LocaleConfig`, el patrón
  `resolveName()` y la validación en dos capas sin re-decidir.
- El *safety net* de fallback evita `null` en la respuesta.

### Negativas / Trade-offs aceptados

- N+1 queries en `findActive`. Si el volumen escala, habrá que
  reevaluar con `@EntityGraph` o `JOIN FETCH`.
- La validación en dos capas implica tocar dos sitios si cambia la
  lista de locales soportados (aunque en la práctica solo cambiaría
  `LocaleConfig` y la constante `SUPPORTED_LOCALES` del servicio, no
  el DTO).
