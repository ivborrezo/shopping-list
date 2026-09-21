# ADR-015: Estrategia de nulabilidad con JSpecify

## Estado

Redactado

## Contexto

La nulabilidad de Java es invisible al sistema de tipos: `String` puede ser
`null` y el compilador no lo sabe. El proyecto la gestionaba por convenio y
comprobaciones manuales, con dos costes concretos:

1. **Ruido del analizador de nulabilidad del IDE.** Al mezclar las clases
   propias del proyecto —sin anotar— con APIs de librerías con cobertura de
   nulidad incompleta (Spring Data, AssertJ, el propio JDK), el IDE generaba
   del orden de 31 advertencias de *"unchecked conversion to @NonNull"*. La
   respuesta provisional fue desactivar el análisis en el IDE
   (`java.compile.nullAnalysis.mode: disabled`), silenciando la señal en
   lugar de aprovecharla.
2. **Los `null` reales se descubrían tarde.** El análisis detectó un NPE
   real en `UserProductService.create`: al crear un producto sin
   `basedOnBaseId` ni `defaultUnit`/`caloriesPer`, el método dereferenciaba
   `base`, que podía ser `null`.

La estrategia adoptada es la opción neutra al IDE que se había aplazado y
registrado como ADR pendiente: anotar el código con JSpecify para que el
análisis dé una señal útil y uniforme, en lugar de desactivarlo o de atar
la nulabilidad a una herramienta concreta.

## Decisiones

### Decisión 1 — JSpecify como único set de anotaciones

Se usa `org.jspecify:jspecify`, el estándar de facto de nulabilidad para
Java y neutro al IDE. La versión la gestiona el BOM de Spring Boot (1.0.0):
la dependencia se declara sin versión explícita y con el scope por defecto
(`compile`). Es un jar de solo anotaciones, sin código **ejecutable**, así
que no añade peso al artefacto.

Descarto `org.jetbrains.annotations`, `org.eclipse.jdt.annotation` y
`org.springframework.lang`: las dos primeras atan el contrato a un IDE
concreto y la tercera es específica de Spring, sin cubrir el proyecto
entero.

### Decisión 2 — `@NullMarked` a nivel de paquete (non-null por defecto)

Cada paquete de `main` que contiene código se marca con `@NullMarked` en su
`package-info.java`: el raíz (`product.service`), los paquetes `category`,
`common`, `config` y `product`, y los subpaquetes por capa (`entity/`,
`repository/`, `service/`, `dto/`, `controller/`). `@NullMarked` no se
propaga a los subpaquetes, así que cada paquete con código lleva su propio
`package-info.java`. Dentro de un paquete marcado todo tipo es non-null por
defecto, y solo admite `null` lo explícitamente anotado con `@Nullable`.

### Decisión 3 — Alcance: solo código `main`, no tests

Las anotaciones se aplican únicamente al código de `main`. Los tests usan
`null` deliberadamente (mocks, datos ad hoc) y anotarlos generaría más
ruido que valor; queda como evolución opcional. La CI no cambia: `javac`
no aplica nulabilidad, así que no hay un gate nuevo que mantener.

### Decisión 4 — Señal de IDE, sin enforcement de build (por ahora)

Las anotaciones dan señal en tiempo de escritura en VSCode (JDT) e
IntelliJ. No se introduce NullAway, Error Prone ni Checker Framework:
serían un gate de build con su coste de configuración, y el objetivo es
visibilidad y contrato, no bloquear el build. El enforcement queda
documentado como evolución opcional.

Trade-off aceptado: sin enforcement, las anotaciones no rompen el build
aunque se incumplan; su valor es preventivo (el editor avisa al escribir)
y documental (el contrato del tipo queda explícito en la firma).

### Decisión 5 — Superficie `@Nullable`

Se marca `@Nullable` exactamente donde el valor puede ser `null`:

- columnas de BD sin `nullable = false` (el campo y sus accessors);
- `getId()` de entidades con identidad autogenerada (`@Nullable Long`), por
  la fricción de JPA: devuelve `null` antes del primer `save()`;
- los identificadores de clave compuesta asignados por la aplicación antes
  de persistir quedan non-null;
- DTOs de request: campos opcionales (todo lo que no sea `@NotNull` de Bean
  Validation);
- DTOs de response: componentes que pueden ser `null` (incluida la
  propagación del `id` de entidad);
- servicios: métodos que devuelven `null`, parámetros opcionales y variables
  locales inicializadas a `null`;
- parámetros opcionales de controller.

Los `@NotNull` de Bean Validation **no se duplican** con JSpecify: son
capas distintas (validación en runtime vs. análisis estático) y conviven.

## Consecuencias

### Positivas

- Señal de nulabilidad uniforme y neutra al IDE (VSCode, IntelliJ), y
  preparada para un eventual enforcement.
- Los warnings de *"unchecked conversion"* del propio código desaparecen por
  la vía correcta: el tipo pasa a non-null por defecto, sin desactivar el
  análisis. Queda una categoría residual en las fronteras con librerías no
  anotadas (el JDK, AssertJ), no accionable desde el código propio; su
  tratamiento se detalla en los trade-offs.
- Los `null` indebidos se detectan en tiempo de escritura en vez de en
  producción; el NPE de `UserProductService.create` es la evidencia del
  coste de la situación anterior.
- Alcance global, replicable en `list-service` por referencia sin reabrir
  el debate, mismo criterio que ADR-011.

### Negativas / Trade-offs aceptados

- **Sin enforcement de build, las anotaciones no son un gate**: no rompen
  el build aunque se incumplan. El valor es preventivo y documental, no de
  verificación automática.
- **Fricción de JPA**: `getId()` y los campos de entidad que pueden ser
  `null` quedan `@Nullable`, lo que obliga a tratar el `id` como opcional
  en el flujo hasta el primer `save()`.
- **Requiere que el IDE/analizador reconozca JSpecify**: es el estándar de
  facto y las herramientas actuales lo soportan, pero es un requisito de
  tooling.
- **El código de test queda fuera del análisis**: los `null` deliberados de
  los tests no se revisan; es una evolución opcional.
- **Fricción con librerías no anotadas (el JDK)**: JDT emite
  `unchecked conversion` en los *method references* que cruzan genéricos del
  JDK (`Function`, `Optional`, `Stream`) y las de AssertJ, porque esas
  librerías no están anotadas. No es accionable desde el código propio; se
  silencia localmente en JDT
  (`org.eclipse.jdt.core.compiler.problem.nullUncheckedConversion=ignore` en
  el `.settings/org.eclipse.jdt.core.prefs` del proyecto, p. ej.
  `services/product-service/.settings/org.eclipse.jdt.core.prefs`, no
  versionado), manteniendo el
  análisis de nulabilidad activo para los avisos accionables. La solución de
  raíz serían *external annotations* de esas librerías, fuera de alcance.

## Alternativas consideradas

### `org.jetbrains.annotations` (descartada)

El set de anotaciones de JetBrains, habitual en proyectos IntelliJ.

**Por qué se descartó:** está atada a JetBrains: su soporte fuera de
IntelliJ es secundario y el contrato de nulabilidad quedaría ligado a una
herramienta concreta, cuando el objetivo es una señal neutra al IDE.

### `org.eclipse.jdt.annotation` (descartada)

El set de anotaciones de Eclipse JDT.

**Por qué se descartó:** está atada a Eclipse; misma razón que la anterior:
ata el contrato a una herramienta concreta.

### `org.springframework.lang` (descartada)

Las anotaciones de nulabilidad de Spring (`@Nullable`, `@NonNull`).

**Por qué se descartó:** es específica de Spring y no cubre el proyecto
entero: el dominio y los servicios no son código Spring y quedarían fuera
del análisis.

### NullAway / Error Prone / Checker Framework (descartada)

Herramientas que hacen cumplir la nulabilidad en tiempo de build.

**Por qué se descartó:** son enforcement de build con su coste de
configuración (plugin, reglas, curva de adopción); el objetivo ahora es
visibilidad y contrato, no bloquear el build. Queda como evolución opcional
documentada en la Decisión 4.

### Desactivar el análisis de nulabilidad del IDE (o no hacer nada) (descartada)

La respuesta provisional que ya se aplicó
(`java.compile.nullAnalysis.mode: disabled`).

**Por qué se descartó:** silencia la señal en lugar de aprovecharla; el NPE
real detectado en `UserProductService.create` evidencia el coste de
descubrir los `null` indebidos tarde.

### `@NullUnmarked` selectivo sobre las entidades (descartada)

Desmarcar las entidades concretas para apagar la nulabilidad donde JPA
genera fricción.

**Por qué se descartó:** mantiene la señal apagada justo donde más fricción
genera, en lugar de anotar la superficie nulable; se prefiere `@Nullable`
explícito para que el contrato quede visible en el tipo.

## Documentación relacionada

- [`docs/contributing/code-style.md`](../contributing/code-style.md) —
  convención de nulabilidad del proyecto (el "qué").
- [ADR-014](../adr/ADR-014-estrategia-de-manejo-de-errores.md) — precedente
  de patrón global replicable con alcance a `list-service`.
- [ADR-011](../adr/ADR-011-estrategia-de-internacionalizacion-y-fallback.md) —
  mismo criterio de patrón global que se replica sin re-decidir.