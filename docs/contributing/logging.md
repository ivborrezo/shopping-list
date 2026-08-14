# Convenciones de logging

- Logs internos **en inglés** siempre, independientemente del `locale` de
  la petición.
- Niveles: `ERROR` (rompe el flujo), `WARN` (anomalía recuperable),
  `INFO` (eventos de negocio), `DEBUG` (detalle técnico, desactivado por
  defecto).
- Salida siempre a **stdout**, nunca a fichero. Formato por perfil:
  `local` = texto plano con `correlationId` entre corchetes; resto =
  JSON estructurado (logstash-logback-encoder).
- **MDC** para contexto ambiental de la petición (`correlationId`, futuro
  `userId`, `locale`); **KV (structured arguments)** para datos puntuales
  del evento (`listId`, `productId`).
- `correlationId`: cabecera `X-Correlation-Id` (se reutiliza si llega, se
  genera un UUID si no); se establece en MDC en un filtro y se limpia con
  `MDC.clear()` en `finally`; se propaga en llamadas REST internas vía
  interceptor del `RestClient` y en eventos como header.

Para el detalle completo (motivos e implementación), ver
[logging.md](../logging.md).
