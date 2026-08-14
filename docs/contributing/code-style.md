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

## Inyección de dependencias

Siempre **por constructor**. Nunca field injection (`@Autowired` en
campo) ni method injection.

Referencia: [ADR-004](../adr/ADR-004-estandares-de-desarrollo-y-gobernanza.md).
