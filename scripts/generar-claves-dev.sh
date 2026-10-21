#!/usr/bin/env bash
# Genera la clave RSA de desarrollo con la que identidad firma los JWT (docs/adr/0008).
# La clave privada no se versiona (infra/claves/*.pem está en .gitignore).
#
# Uso: scripts/generar-claves-dev.sh [--forzar]
set -euo pipefail

DIRECTORIO="$(cd "$(dirname "$0")/.." && pwd)/infra/claves"
CLAVE="$DIRECTORIO/jwt-privada.pem"

mkdir -p "$DIRECTORIO"

if [[ -f "$CLAVE" && "${1:-}" != "--forzar" ]]; then
	echo "Ya existe $CLAVE (usa --forzar para regenerarla; los tokens emitidos dejarán de valer)"
	exit 0
fi

# PKCS#8 sin cifrar, 2048 bits: el formato que lee identidad (ClaveFirma.desdePem)
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$CLAVE"
echo "Clave generada en $CLAVE"
