#!/usr/bin/env bash
# Reconstruye las proyecciones de consultas releyendo los topics de Kafka (docs/adr/0010).
#
# Sube la versión de la proyección (la guarda en .env para que compose la use también en los siguientes
# arranques), recrea el contenedor de consultas y espera a que el retraso de la proyección llegue a 0.
# Al arrancar con otra versión, consultas vacía sus tablas y relee los topics desde el principio.
#
# Uso: scripts/reconstruir-consultas.sh [version]   (sin versión: la actual + 1)
set -euo pipefail
cd "$(dirname "$0")/.."

actual=$(docker compose exec -T mariadb mariadb -uconsultas -pconsultas-dev consultas -N \
	-e "SELECT COALESCE(MAX(version), 0) FROM proyeccion_estado" 2>/dev/null || echo 0)
nueva=${1:-$((actual + 1))}
echo "Proyeccion de consultas: version $actual -> $nueva"

touch .env
if grep -q '^GASTOS_CONSULTAS_PROYECCION_VERSION=' .env; then
	sed -i "s/^GASTOS_CONSULTAS_PROYECCION_VERSION=.*/GASTOS_CONSULTAS_PROYECCION_VERSION=$nueva/" .env
else
	echo "GASTOS_CONSULTAS_PROYECCION_VERSION=$nueva" >> .env
fi

inicio=$(date +%s)
docker compose up -d --wait consultas

echo "Esperando a que la proyeccion alcance a los topics..."
until retraso=$(curl -fs http://127.0.0.1:8085/actuator/health/proyeccion | sed -n 's/.*"retraso":\([0-9]*\).*/\1/p') \
		&& [ -n "$retraso" ] && [ "$retraso" -eq 0 ]; do
	echo "  retraso: ${retraso:-?} eventos"
	sleep 2
done
echo "Reconstruida en $(( $(date +%s) - inicio )) s"
