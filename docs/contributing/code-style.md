# Estilo de código y linters

- **Java**: Checkstyle como linter (ruleset Google Java Style en
  [`config/checkstyle/checkstyle.xml`](../../config/checkstyle/checkstyle.xml))
  y Spotless (Google Java Format) como formatter.
- **JavaScript/React** (Fase 2, no implementado aún): ESLint como linter
  y Prettier como formatter.

## Javadocs

- Se redactan **en español**, según el estándar Oracle/Sun alineado con
  Google Java Style §7.
- Todo tipo público/protected y todo método público de 2 o más líneas
  lleva Javadoc.
- Reglas: la primera frase es el *summary*; los tags van en orden
  `@param → @return → @throws → @deprecated`; párrafos adicionales con
  `<p>`; referencias inline con `{@link}`/`{@code}`.
- Estilo **definitivo**: describen el contrato actual como si fuera su
  forma final, sin menciones a features futuros.
- `package-info.java` documenta cada paquete.

## Nulabilidad

- Set de anotaciones: JSpecify (`org.jspecify:jspecify`), neutro al IDE. La
  versión la gestiona el BOM de Spring Boot.
- Cada paquete de `main` con código se marca con `@NullMarked` en su
  `package-info.java`: todo tipo es non-null por defecto y solo lo
  explícitamente `@Nullable` admite `null`. `@NullMarked` no se propaga a los
  subpaquetes, así que cada paquete por capa (`entity/`, `repository/`,
  `service/`, `dto/`, `controller/`) lleva también su `package-info.java`.
- Se anota `@Nullable` solo la superficie realmente nulable: columnas de BD sin
  `nullable = false`, `getId()` de entidades sin persistir, campos opcionales de
  DTOs, retornos y variables que pueden ser `null`.
- Los `@NotNull`/`@NotBlank`/`@Size` de Bean Validation no se duplican con
  JSpecify: son capas distintas (validación en runtime vs. análisis estático).
- El código de test no se anota.
- Al crear un paquete nuevo en `main`, crear también su `package-info.java` con
  `@NullMarked`.
- En un record, se anota el componente (`@Nullable String x`), lo que cubre el
  campo, el accessor y el constructor.
- En entidades JPA con id autogenerado (`@GeneratedValue`), `getId()` es
  `@Nullable` (no existe antes del `persist`); las columnas con `nullable = false`
  son non-null.

Referencia: [ADR-015](../adr/ADR-015-estrategia-de-nulabilidad-con-jspecify.md).

## Inyección de dependencias

Siempre **por constructor**. Nunca field injection (`@Autowired` en
campo) ni method injection.

Referencia: [ADR-004](../adr/ADR-004-estandares-de-desarrollo-y-gobernanza.md).
