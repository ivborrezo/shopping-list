#!/usr/bin/env bash
# Verifica que el soporte de tests compartido entre product-service y
# list-service es idéntico byte a byte, normalizando los finales de línea
# (CRLF/LF) para no depender del sistema donde se clone el repositorio.
#
# Compara una lista explícita de ficheros y sale con código distinto de cero
# si alguno falta o difiere entre ambos servicios.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

REFERENCE_SERVICE="product-service"
TARGET_SERVICE="list-service"

SHARED_FILES=(
  "src/test/java/dev/ivborrezo/shoppinglist/testsupport/TestUsers.java"
  "src/test/java/dev/ivborrezo/shoppinglist/testsupport/TestRsaKey.java"
  "src/test/java/dev/ivborrezo/shoppinglist/testsupport/TestJwtFactory.java"
  "src/test/java/dev/ivborrezo/shoppinglist/testsupport/TestJwksServer.java"
  "src/test/java/dev/ivborrezo/shoppinglist/testsupport/TestAuth.java"
  "src/test/resources/test-keys/test-rsa-private.pem"
  "src/test/resources/test-keys/test-rsa-public.pem"
)

fail() {
  printf 'check-test-support: %s\n' "$*" >&2
  exit 1
}

main() {
  local relative reference target

  for relative in "${SHARED_FILES[@]}"; do
    reference="services/${REFERENCE_SERVICE}/${relative}"
    target="services/${TARGET_SERVICE}/${relative}"

    [[ -f "$reference" ]] || fail "falta el fichero de referencia $reference"
    [[ -f "$target" ]] || fail "falta el fichero $target"

    if ! cmp -s <(tr -d '\r' <"$reference") <(tr -d '\r' <"$target"); then
      fail "$target difiere de $reference"
    fi
  done

  printf 'check-test-support: %d ficheros compartidos idénticos\n' "${#SHARED_FILES[@]}"
}

main
