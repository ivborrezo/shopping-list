# ADR-006: Identificación de propietario sin autenticación real (Fase 1-3)

## Estado

Aceptado.

## Contexto

`product-service` (productos de usuario) y `list-service` (listas e
ítems) necesitan asociar cada recurso a un propietario desde Fase 1,
pero no existe ningún sistema de identidad hasta Fase 4
(`auth-service`, Keycloak + OAuth2/OIDC). Es necesario decidir cómo se
identifica al "usuario" durante las Fases 1-3, sin bloquear el
desarrollo del resto del sistema a la espera de la autenticación real.

Aunque en apariencia es un detalle de implementación menor, cualifica
para ADR por el coste real de revertirlo, no por su complejidad
técnica:

- Cualquier dato creado en Fase 1-3 (demos públicas, capturas para
  portafolio, pruebas) tendrá un `ownerId` arbitrario sin relación con
  un usuario real de Keycloak.
- Es una decisión de **seguridad explícita** (cualquiera puede pasar
  cualquier `ownerId` y "suplantar" la propiedad de cualquier
  lista/producto) que, sin documentar, podría confundirse con una
  vulnerabilidad no intencionada en vez de una decisión consciente.
- Afecta al contrato de API de **ambos servicios** a la vez, no es una
  decisión interna de uno solo.

### Alternativas consideradas

1. **Cabecera custom** (ej. `X-Debug-User-Id`): más parecida a como se
   recibirá el usuario autenticado en Fase 4 (vía cabecera/token), pero
   añade una convención de transporte adicional que también habrá que
   descartar por completo en Fase 4. Se descarta.
2. **`ownerId` como campo del body** (UUID), sin validar contra ningún
   proveedor de identidad: más simple, más visible como placeholder
   (viaja junto al resto de datos del recurso, no oculto en una
   cabecera), y su eliminación en Fase 4 es un cambio de contrato más
   directo. Elegida.

## Decisión

`product-service` y `list-service` aceptan un campo `ownerId` (UUID) en
el body de las peticiones de creación (`POST /products`, `POST
/lists`), sin validarlo contra ningún proveedor de identidad.

- **Validación de formato, no de identidad:** se valida que `ownerId`
  sea un UUID sintácticamente bien formado (Bean Validation,
  `@Pattern`/`@UUID` en el DTO de entrada), devolviendo `400 Bad
  Request` si no lo es. Esto no mitiga el riesgo de suplantación en
  absoluto — sigue siendo trivial enviar un UUID ajeno válido — su
  único propósito es evitar que un valor mal formado llegue a la capa
  de persistencia (columna `UUID` en Postgres) y falle allí con una
  excepción SQL poco clara en vez de un error de validación limpio en
  el borde de la API.
- **No existe identidad real detrás del dato:** no se valida que el
  `ownerId` recibido corresponda a ningún usuario existente, porque no
  hay ningún registro de usuarios en Fase 1-3.
- **Sin registro compartido entre servicios:** `product-service` y
  `list-service` no comparten ninguna tabla ni servicio de usuarios, ni
  siquiera como placeholder. Cada uno confía en el `ownerId` que recibe
  de forma independiente, coherente con Database-per-Service y evitando
  introducir acoplamiento nuevo para una pieza que se va a eliminar por
  completo en Fase 4.
- **Riesgo de suplantación aceptado sin mitigación adicional:**
  cualquiera puede acceder o modificar recursos de cualquier otro
  `ownerId` con solo conocerlo o adivinarlo. No se introduce ninguna
  medida compensatoria (rate limiting, ofuscación, etc.), porque las
  Fases 1-3 no pretenden ser una versión estable de cara a usuarios
  reales — su propósito es validar que el sistema funciona
  end-to-end, no proteger datos de valor.

## Consecuencias

- El código de ambos servicios trata `ownerId` como un dato de entrada
  más, sin lógica de autorización asociada (cualquier request con
  cualquier `ownerId` es válido si el UUID está bien formado).
- **Sin plan de migración de datos a Fase 4:** los datos creados en
  Fase 1-3 (incluida cualquier demo pública) se descartan por completo
  al implementar `auth-service`. No se intenta reasociar el `ownerId`
  placeholder a un usuario real de Keycloak — no existe una
  correspondencia significativa que preservar, y forzarla sería más
  complejo y menos honesto que empezar de cero.
- Este ADR queda **superado** en el momento en que se implemente
  `ADR-XXF4-keycloak-como-identity-provider`, que debe referenciar este
  documento como `Supersedes`. A partir de ese momento, el `ownerId` de
  cada recurso se deriva del token de autenticación (JWT/OIDC), no de
  un campo de entrada del body.
- Cuando se redacte `ADR-XXF4-keycloak-como-identity-provider`, este
  documento cambia su estado a `Superseded by ADR-XXF4-keycloak-como-identity-provider`,
  y no se elimina — queda en `docs/adr/` como registro histórico de por
  qué el sistema funcionó así durante tres fases.
- Revisar también en Fase 4, junto con esta decisión, el uso de `Long`
  autoincremental (en vez de `UUID`) como identificador de recursos
  (`list`, `product`): el
  riesgo de enumeración de recursos que `UUID` mitigaría es hoy
  secundario frente al riesgo mayor y previo que resuelve este ADR.
