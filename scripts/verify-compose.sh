#!/usr/bin/env bash
# Verificación E2E del despliegue compuesto de ShoppingList.
#
# Levanta el sistema con Docker Compose, recorre las lecturas y escrituras
# públicas por http://localhost:8080 y comprueba que solo el gateway publica
# un puerto HTTP y que las bases de datos quedan en loopback. Sale con código
# distinto de cero ante cualquier fallo y deja el sistema arriba por defecto
# para poder inspeccionar los logs; KEEP=0 lo baja al terminar.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

BASE_URL="http://localhost:8080"
OWNER_ID="3f2504e0-4f89-41d3-9a0c-0305e82c3301"
KEEP="${KEEP:-1}"

TMP_DIR="$(mktemp -d)"
HEADERS_FILE="${TMP_DIR}/headers"
BODY_FILE="${TMP_DIR}/body"

cleanup() {
  local exit_code=$?
  rm -rf "$TMP_DIR"
  if [[ "$KEEP" == "0" ]]; then
    docker compose down
  fi
  exit "$exit_code"
}
trap cleanup EXIT

fail() {
  printf 'verify-compose: %s\n' "$*" >&2
  exit 1
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "falta el comando '$1' en el PATH"
}

require_command docker
require_command curl
require_command sed

wait_for_health() {
  local attempts=60
  for ((i = 1; i <= attempts; i++)); do
    if curl -fsS "${BASE_URL}/actuator/health" >/dev/null 2>&1; then
      return 0
    fi
    sleep 2
  done
  fail "el gateway no responde en ${BASE_URL}/actuator/health tras $((attempts * 2))s"
}

assert_status() {
  local expected="$1"
  local actual="$2"
  local context="$3"
  [[ "$actual" == "$expected" ]] || fail "$context respondió $actual, se esperaba $expected"
}

post_json() {
  local path="$1"
  local payload="$2"
  local context="$3"
  local status

  status="$(
    curl -sS -D "$HEADERS_FILE" -o "$BODY_FILE" -w '%{http_code}' \
      -X POST "${BASE_URL}${path}" \
      -H 'Content-Type: application/json' \
      -d "$payload"
  )"
  assert_status 201 "$status" "$context"
}

read_location() {
  sed -n 's/^[Ll]ocation: *//p' "$HEADERS_FILE" | tr -d '\r' | head -n 1
}

read_by_location() {
  local location="$1"
  local context="$2"
  [[ "$location" == /* ]] || fail "$context devolvió un Location no relativo: '$location'"
  curl -fsS "${BASE_URL}${location}" >/dev/null ||
    fail "la lectura de ${BASE_URL}${location} ($context) falló"
}

assert_loopback_port() {
  local service="$1"
  local internal_port="$2"
  local expected="$3"
  local actual

  actual="$(docker compose port "$service" "$internal_port" 2>/dev/null || true)"
  [[ "$actual" == "$expected" ]] ||
    fail "$service expone '$actual' en $internal_port, se esperaba '$expected'"
}

assert_port_not_published() {
  local service="$1"
  local internal_port="$2"
  local output

  output="$(docker compose port "$service" "$internal_port" 2>/dev/null || true)"
  # Compose imprime "invalid IP:0" cuando el puerto del contenedor no tiene
  # publicación en el host; cualquier otra salida es una mapping real.
  case "$output" in
    "" | "invalid IP:0") ;;
    *) fail "$service publica $internal_port ('$output'), no debería publicar ningún puerto HTTP" ;;
  esac
}

assert_tcp_reachable() {
  local host="$1"
  local port="$2"
  local context="$3"
  if ! (exec 3<>"/dev/tcp/${host}/${port}") 2>/dev/null; then
    fail "$context no es alcanzable en ${host}:${port}"
  fi
}

assert_tcp_unreachable() {
  local host="$1"
  local port="$2"
  local context="$3"
  if (exec 3<>"/dev/tcp/${host}/${port}") 2>/dev/null; then
    fail "$context es alcanzable en ${host}:${port}, no debería publicarse"
  fi
}

main() {
  printf 'verify-compose: levantando el sistema\n'
  docker compose up -d --build

  printf 'verify-compose: esperando el health del gateway\n'
  wait_for_health

  printf 'verify-compose: lecturas públicas\n'
  curl -fsS "${BASE_URL}/categories" >/dev/null || fail "GET /categories falló"
  curl -fsS "${BASE_URL}/lists?ownerId=${OWNER_ID}" >/dev/null || fail "GET /lists falló"

  printf 'verify-compose: escritura y lectura de listas\n'
  post_json "/lists" \
    "{\"ownerId\":\"${OWNER_ID}\",\"name\":\"verificacion compose\"}" \
    "POST /lists"
  local list_location
  list_location="$(read_location)"
  [[ -n "$list_location" ]] || fail "POST /lists no devolvió Location"
  read_by_location "$list_location" "POST /lists"

  printf 'verify-compose: escritura y lectura de productos de usuario\n'
  post_json "/user-products" \
    "{\"ownerId\":\"${OWNER_ID}\",\"name\":\"Producto de verificacion\",\"defaultUnit\":\"UNIT\",\"caloriesPer\":\"UNIT\"}" \
    "POST /user-products"
  local product_location
  product_location="$(read_location)"
  [[ -n "$product_location" ]] || fail "POST /user-products no devolvió Location"
  read_by_location "$product_location" "POST /user-products"

  printf 'verify-compose: puertos publicados\n'
  local gateway_port
  gateway_port="$(docker compose port api-gateway 8080 2>/dev/null || true)"
  case "$gateway_port" in
    "0.0.0.0:8080" | "[::]:8080") ;;
    *) fail "api-gateway expone '$gateway_port' en 8080, se esperaba el puerto 8080" ;;
  esac
  assert_loopback_port product-db 5432 "127.0.0.1:5434"
  assert_loopback_port list-db 5432 "127.0.0.1:5435"
  assert_port_not_published product-service 8081
  assert_port_not_published list-service 8082
  assert_tcp_unreachable 127.0.0.1 8081 "product-service (HTTP interno)"
  assert_tcp_unreachable 127.0.0.1 8082 "list-service (HTTP interno)"

  printf 'verify-compose: bases de datos en loopback\n'
  assert_tcp_reachable 127.0.0.1 5434 "product-db"
  assert_tcp_reachable 127.0.0.1 5435 "list-db"

  printf 'verify-compose: OK\n'
}

main
