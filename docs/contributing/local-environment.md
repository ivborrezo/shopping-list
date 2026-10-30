# Entorno local

Convenciones del entorno de desarrollo local. El proceso completo de
instalación y arranque del repositorio se describe en el
[setup](setup.md).

## Puertos

- Solo `api-gateway` publica un puerto HTTP en el host: `8080`.
- `product-service` y `list-service` quedan en la red interna de Compose;
  sus puertos `8081`/`8082` solo se usan en el modo "servicios a mano"
  (perfil `local` arrancado con `mvnw` en el host).
- Las bases de datos se exponen al host en puertos **no estándar** y
  enlazadas explícitamente a loopback:
  - `product-db` → `127.0.0.1:5434`
  - `list-db` → `127.0.0.1:5435`
- El puerto interno del contenedor es siempre `5432`. El binding
  `127.0.0.1` es una decisión operativa (H-06 CA-03): permite depurar desde
  el host sin exponer las bases de datos en la red.
- El formato de publicación está fijado en `docker-compose.yml`; no se
  configura con `PRODUCT_DB_PORT_OUT`/`LIST_DB_PORT_OUT`. Esas variables
  siguen disponibles para el perfil `local` de los servicios cuando se
  arrancan contra una base de datos en el host.

## Nomenclatura

- Contenedores con prefijo `shopping-list-`: bases de datos
  (`shopping-list-product-db`, `shopping-list-list-db`) y servicios
  (`shopping-list-product-service`, `shopping-list-list-service`).
- Red Docker: `shopping-list-net`.

## Variables de entorno

- Configuración por servicio en `.env` (plantilla `.env.example`):
  `PRODUCT_DB_*` y `LIST_DB_*` (`HOST`, `NAME`, `USER`, `PASSWORD`, `PORT_OUT`).

Referencias: [Setup local de product-service](../../services/product-service/docs/local-setup.md) ·
[Setup local de list-service](../../services/list-service/docs/local-setup.md) ·
[Estrategia de CI/CD](../cicd/cicd-strategy.md).
