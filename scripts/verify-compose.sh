#!/usr/bin/env bash
# Verificación E2E del despliegue compuesto de ShoppingList.
#
# Levanta el sistema con Docker Compose, espera al proveedor OIDC y al
# gateway, obtiene tokens reales del realm local y recorre los escenarios de
# autenticación, aislamiento por propietario y autorización por rol a través
# de http://localhost:8080. Comprueba además que solo el gateway publica un
# puerto HTTP y que las bases de datos y el proveedor de identidad quedan en
# loopback. Sale con código distinto de cero ante cualquier fallo y deja el
# sistema arriba por defecto para poder inspeccionar los logs; KEEP=0 lo baja
# al terminar.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

BASE_URL="http://localhost:8080"
AUTH_URL="http://127.0.0.1:8083"
REALM="shopping-list"
EXPECTED_ISSUER="http://localhost:8083/realms/${REALM}"
E2E_CLIENT_ID="e2e-client"

# Usuarios semilla del realm local (services/auth-service/realm).
USER_EMAIL="user@shopping-list.local"
USER_CREDENTIAL="user123"
USER_ID="11111111-1111-4111-8111-111111111111"
ADMIN_EMAIL="admin@shopping-list.local"
ADMIN_CREDENTIAL="admin123"
ADMIN_ID="99999999-9999-4999-8999-999999999999"

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
require_command tr
require_command base64

wait_for_url() {
  local url="$1"
  local context="$2"
  local attempts=90
  for ((i = 1; i <= attempts; i++)); do
    if curl -fsS "$url" >/dev/null 2>&1; then
      return 0
    fi
    sleep 2
  done
  fail "$context no responde en $url tras $((attempts * 2))s"
}

assert_status() {
  local expected="$1"
  local actual="$2"
  local context="$3"
  [[ "$actual" == "$expected" ]] || fail "$context respondió $actual, se esperaba $expected"
}

assert_status_in() {
  local context="$1"
  local actual="$2"
  shift 2
  local expected
  for expected in "$@"; do
    [[ "$actual" == "$expected" ]] && return 0
  done
  fail "$context respondió $actual, se esperaba uno de: $*"
}

assert_body_contains() {
  local expected="$1"
  local context="$2"
  grep -q -- "$expected" "$BODY_FILE" ||
    fail "$context no contiene '$expected'"
}

request() {
  local method="$1"
  local path="$2"
  local token="${3:-}"
  local payload="${4:-}"
  local args=(-sS -D "$HEADERS_FILE" -o "$BODY_FILE" -w '%{http_code}' -X "$method" "${BASE_URL}${path}")

  if [[ -n "$token" ]]; then
    args+=(-H "Authorization: Bearer ${token}")
  fi
  if [[ -n "$payload" ]]; then
    args+=(-H 'Content-Type: application/json' -d "$payload")
  fi
  curl "${args[@]}"
}

create_resource() {
  local path="$1"
  local token="$2"
  local payload="$3"
  local context="$4"
  local status

  status="$(request POST "$path" "$token" "$payload")"
  assert_status 201 "$status" "$context"
}

read_location() {
  sed -n 's/^[Ll]ocation: *//p' "$HEADERS_FILE" | tr -d '\r' | head -n 1
}

location_id() {
  read_location | sed 's#.*/##'
}

fetch_access_token() {
  local username="$1"
  local password="$2"
  local response

  response="$(
    curl -fsS -X POST \
      "${AUTH_URL}/realms/${REALM}/protocol/openid-connect/token" \
      -H 'Content-Type: application/x-www-form-urlencoded' \
      --data-urlencode 'grant_type=password' \
      --data-urlencode "client_id=${E2E_CLIENT_ID}" \
      --data-urlencode "username=${username}" \
      --data-urlencode "password=${password}"
  )" || fail "no se pudo obtener un token para ${username}"

  printf '%s' "$response" | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p'
}

jwt_payload() {
  local encoded
  encoded="$(printf '%s' "$1" | sed 's/^[^.]*\.\([^.]*\)\..*$/\1/')"
  encoded="${encoded//-/+}"
  encoded="${encoded//_/\/}"
  case $((${#encoded} % 4)) in
    2) encoded="${encoded}==" ;;
    3) encoded="${encoded}=" ;;
  esac
  printf '%s' "$encoded" | base64 -d 2>/dev/null
}

assert_payload_contains() {
  local payload="$1"
  local expected="$2"
  local context="$3"
  grep -q -- "$expected" <<<"$payload" || fail "$context no contiene '$expected'"
}

assert_oidc_endpoints() {
  local discovery="${AUTH_URL}/realms/${REALM}/.well-known/openid-configuration"
  local base="${AUTH_URL}/realms/${REALM}/protocol/openid-connect"
  local status

  curl -fsS "$discovery" >"$BODY_FILE" || fail "GET ${discovery} falló"
  assert_body_contains "\"issuer\":\"${EXPECTED_ISSUER}\"" "el documento de discovery"

  status="$(curl -sS -o "$BODY_FILE" -w '%{http_code}' "${base}/certs")"
  assert_status 200 "$status" "GET ${base}/certs"

  status="$(curl -sS -o "$BODY_FILE" -w '%{http_code}' -X POST "${base}/token")"
  assert_status_in "POST ${base}/token sin parámetros" "$status" 200 400

  status="$(curl -sS -o "$BODY_FILE" -w '%{http_code}' "${base}/auth")"
  assert_status_in "GET ${base}/auth sin parámetros" "$status" 200 302 400
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

  printf 'verify-compose: esperando el health de auth-service\n'
  wait_for_url \
    "${AUTH_URL}/realms/${REALM}/.well-known/openid-configuration" \
    "auth-service"

  printf 'verify-compose: esperando el health del gateway\n'
  wait_for_url "${BASE_URL}/actuator/health" "el gateway"

  printf 'verify-compose: endpoints OIDC\n'
  assert_oidc_endpoints

  printf 'verify-compose: autenticación obligatoria\n'
  local status
  status="$(request GET /categories)"
  assert_status 401 "$status" "GET /categories sin token"
  assert_body_contains '"code":"UNAUTHENTICATED"' "GET /categories sin token"
  status="$(request GET /lists)"
  assert_status 401 "$status" "GET /lists sin token"
  assert_body_contains '"code":"UNAUTHENTICATED"' "GET /lists sin token"

  printf 'verify-compose: tokens del realm local\n'
  local user_jwt admin_jwt payload
  user_jwt="$(fetch_access_token "$USER_EMAIL" "$USER_CREDENTIAL")"
  [[ -n "$user_jwt" ]] || fail "el token de ${USER_EMAIL} no incluye access_token"
  payload="$(jwt_payload "$user_jwt")"
  assert_payload_contains "$payload" "\"sub\":\"${USER_ID}\"" "el token de ${USER_EMAIL}"
  assert_payload_contains "$payload" '"roles":\[[^]]*"USER"' "el token de ${USER_EMAIL}"

  admin_jwt="$(fetch_access_token "$ADMIN_EMAIL" "$ADMIN_CREDENTIAL")"
  [[ -n "$admin_jwt" ]] || fail "el token de ${ADMIN_EMAIL} no incluye access_token"
  payload="$(jwt_payload "$admin_jwt")"
  assert_payload_contains "$payload" "\"sub\":\"${ADMIN_ID}\"" "el token de ${ADMIN_EMAIL}"
  assert_payload_contains "$payload" '"roles":\[[^]]*"ADMIN"' "el token de ${ADMIN_EMAIL}"

  printf 'verify-compose: creación autenticada sin ownerId\n'
  create_resource "/lists" "$user_jwt" '{"name":"verificacion compose"}' "POST /lists"
  local user_list_location user_list_id
  user_list_location="$(read_location)"
  [[ -n "$user_list_location" ]] || fail "POST /lists no devolvió Location"
  user_list_id="$(location_id)"
  assert_body_contains "\"ownerId\":\"${USER_ID}\"" "POST /lists"

  create_resource "/user-products" "$user_jwt" \
    '{"name":"Producto de verificacion","defaultUnit":"UNIT","caloriesPer":"UNIT"}' \
    "POST /user-products"
  local user_product_location user_product_id
  user_product_location="$(read_location)"
  [[ -n "$user_product_location" ]] || fail "POST /user-products no devolvió Location"
  user_product_id="$(location_id)"
  assert_body_contains "\"ownerId\":\"${USER_ID}\"" "POST /user-products"

  status="$(request GET /lists "$user_jwt")"
  assert_status 200 "$status" "GET /lists"
  status="$(request GET "$user_list_location" "$user_jwt")"
  assert_status 200 "$status" "GET ${user_list_location}"
  status="$(request GET "$user_product_location" "$user_jwt")"
  assert_status 200 "$status" "GET ${user_product_location}"

  printf 'verify-compose: ítem en la lista propia\n'
  status="$(request POST "/lists/${user_list_id}/items" "$user_jwt" \
    "{\"productId\":\"${user_product_id}\",\"productType\":\"USER\"}")"
  assert_status 201 "$status" "POST /lists/${user_list_id}/items"

  printf 'verify-compose: aislamiento entre usuarios\n'
  status="$(request GET "$user_list_location" "$admin_jwt")"
  assert_status 403 "$status" "GET ${user_list_location} con otro usuario"
  assert_body_contains '"code":"OWNER_MISMATCH"' "GET ${user_list_location} con otro usuario"

  status="$(request GET "$user_product_location" "$admin_jwt")"
  assert_status 403 "$status" "GET ${user_product_location} con otro usuario"
  assert_body_contains '"code":"OWNER_MISMATCH"' "GET ${user_product_location} con otro usuario"

  create_resource "/lists" "$admin_jwt" '{"name":"verificacion compose admin"}' \
    "POST /lists con otro usuario"
  local admin_list_id
  admin_list_id="$(location_id)"
  [[ -n "$admin_list_id" ]] || fail "POST /lists con otro usuario no devolvió Location"
  assert_body_contains "\"ownerId\":\"${ADMIN_ID}\"" "POST /lists con otro usuario"

  status="$(request POST "/lists/${admin_list_id}/items" "$admin_jwt" \
    "{\"productId\":\"${user_product_id}\",\"productType\":\"USER\"}")"
  assert_status 400 "$status" "POST /lists/${admin_list_id}/items con producto ajeno"
  assert_body_contains '"code":"INVALID_PRODUCT_REFERENCE"' \
    "POST /lists/${admin_list_id}/items con producto ajeno"

  printf 'verify-compose: autorización por rol en el catálogo global\n'
  local category_code category_payload
  category_code="VERIFY_$(date +%s)"
  category_payload="{\"code\":\"${category_code}\",\"isActive\":true,\"translations\":[{\"locale\":\"es\",\"name\":\"${category_code}\"}]}"

  status="$(request POST /categories "$user_jwt" "$category_payload")"
  assert_status 403 "$status" "POST /categories con rol USER"
  assert_body_contains '"code":"ACCESS_DENIED"' "POST /categories con rol USER"

  create_resource "/categories" "$admin_jwt" "$category_payload" "POST /categories con rol ADMIN"

  printf 'verify-compose: puertos publicados\n'
  local gateway_port
  gateway_port="$(docker compose port api-gateway 8080 2>/dev/null || true)"
  case "$gateway_port" in
    "0.0.0.0:8080" | "[::]:8080") ;;
    *) fail "api-gateway expone '$gateway_port' en 8080, se esperaba el puerto 8080" ;;
  esac
  assert_loopback_port product-db 5432 "127.0.0.1:5434"
  assert_loopback_port list-db 5432 "127.0.0.1:5435"
  assert_loopback_port auth-db 5432 "127.0.0.1:5436"
  assert_loopback_port auth-service 8080 "127.0.0.1:8083"
  assert_port_not_published product-service 8081
  assert_port_not_published list-service 8082
  assert_tcp_unreachable 127.0.0.1 8081 "product-service (HTTP interno)"
  assert_tcp_unreachable 127.0.0.1 8082 "list-service (HTTP interno)"

  printf 'verify-compose: bases de datos e identidad en loopback\n'
  assert_tcp_reachable 127.0.0.1 5434 "product-db"
  assert_tcp_reachable 127.0.0.1 5435 "list-db"
  assert_tcp_reachable 127.0.0.1 5436 "auth-db"
  assert_tcp_reachable 127.0.0.1 8083 "auth-service"

  printf 'verify-compose: OK\n'
}

main
