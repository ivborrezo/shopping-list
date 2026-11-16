#!/usr/bin/env bash
# Verifica que el soporte de tests compartido entre los servicios es idéntico
# byte a byte, normalizando los finales de línea (CRLF/LF) para no depender
# del sistema donde se clone el repositorio.
#
# Compara contra product-service una lista explícita de ficheros en
# list-service y api-gateway, y sale con código distinto de cero si alguno
# falta o difiere. El soporte exclusivo de los servicios con dominio
# (TestAuth.java) solo se compara entre product-service y list-service.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

REFERENCE_SERVICE="product-service"

SHARED_TARGET_SERVICES=(
  "list-service"
  "api-gateway"
)

DOMAIN_TARGET_SERVICES=(
  "list-service"
)

SHARED_FILES=(
  "src/test/java/dev/ivborrezo/shoppinglist/testsupport/TestUsers.java"
  "src/test/java/dev/ivborrezo/shoppinglist/testsupport/TestRsaKey.java"
  "src/test/java/dev/ivborrezo/shoppinglist/testsupport/TestJwtFactory.java"
  "src/test/java/dev/ivborrezo/shoppinglist/testsupport/TestJwksServer.java"
  "src/test/resources/test-keys/test-rsa-private.pem"
  "src/test/resources/test-keys/test-rsa-public.pem"
)

DOMAIN_FILES=(
  "src/test/java/dev/ivborrezo/shoppinglist/testsupport/TestAuth.java"
)

fail() {
  printf 'check-test-support: %s\n' "$*" >&2
  exit 1
}

assert_identical() {
  local relative="$1"
  local target_service="$2"
  local reference="services/${REFERENCE_SERVICE}/${relative}"
  local target="services/${target_service}/${relative}"

  [[ -f "$reference" ]] || fail "falta el fichero de referencia $reference"
  [[ -f "$target" ]] || fail "falta el fichero $target"

  if ! cmp -s <(tr -d '\r' <"$reference") <(tr -d '\r' <"$target"); then
    fail "$target difiere de $reference"
  fi
}

main() {
  local relative target_service

  for relative in "${SHARED_FILES[@]}"; do
    for target_service in "${SHARED_TARGET_SERVICES[@]}"; do
      assert_identical "$relative" "$target_service"
    done
  done

  for relative in "${DOMAIN_FILES[@]}"; do
    for target_service in "${DOMAIN_TARGET_SERVICES[@]}"; do
      assert_identical "$relative" "$target_service"
    done
  done

  local checked=$(( ${#SHARED_FILES[@]} * ${#SHARED_TARGET_SERVICES[@]} ))
  checked=$(( checked + ${#DOMAIN_FILES[@]} * ${#DOMAIN_TARGET_SERVICES[@]} ))
  printf 'check-test-support: %d comparaciones idénticas\n' "$checked"
}

main
