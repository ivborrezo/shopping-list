# Entorno local

Convenciones del entorno de desarrollo local. El proceso completo de
instalación y arranque del repositorio se describe en el
[setup](setup.md).

## Puertos de base de datos

- Las bases de datos se exponen al host en puertos **no estándar** para
  evitar conflicto con una instancia local de PostgreSQL:
  - `product-db` → host `5434`
  - `list-db` → host `5435`
- El puerto interno del contenedor es siempre `5432` (formato
  `"HOST:CONTENEDOR"` en `docker-compose.yml`).
- Configurables vía `.env` (`PRODUCT_DB_PORT_OUT`, `LIST_DB_PORT_OUT`).

## Nomenclatura

- Contenedores con prefijo `shopping-list-`
  (`shopping-list-product-db`, `shopping-list-list-db`).
- Red Docker: `shopping-list-net`.

## Variables de entorno

- Configuración por servicio en `.env` (plantilla `.env.example`):
  `PRODUCT_DB_*` y `LIST_DB_*` (`NAME`, `USER`, `PASSWORD`, `PORT_OUT`).

Referencias: [Setup local de product-service](../../product-service/docs/local-setup.md) ·
[Estrategia de CI/CD](../cicd/cicd-strategy.md).
