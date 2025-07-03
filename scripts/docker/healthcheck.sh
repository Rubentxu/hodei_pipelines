#!/bin/bash

# Health check script para el worker de Hodei Pipelines

# Verificar que el proceso Java esté ejecutándose
if ! pgrep -f "hodei-worker.jar" > /dev/null; then
    echo "ERROR: Worker process not running"
    exit 1
fi

# Verificar que el directorio de trabajo esté accesible
if [ ! -d "$HODEI_WORK_DIR" ] || [ ! -w "$HODEI_WORK_DIR" ]; then
    echo "ERROR: Work directory not accessible: $HODEI_WORK_DIR"
    exit 1
fi

# Verificar conectividad con el orquestador (sin bloquear)
if ! timeout 5 bash -c "</dev/tcp/$HODEI_ORCHESTRATOR_HOST/$HODEI_ORCHESTRATOR_PORT" 2>/dev/null; then
    echo "WARNING: Cannot connect to orchestrator at $HODEI_ORCHESTRATOR_HOST:$HODEI_ORCHESTRATOR_PORT"
    # No falla el health check por esto, podría ser temporal
fi

# Verificar uso de memoria
if command -v free >/dev/null; then
    MEMORY_USAGE=$(free | grep Mem | awk '{printf "%.0f", ($3/$2)*100}')
    if [ "$MEMORY_USAGE" -gt 95 ]; then
        echo "WARNING: High memory usage: ${MEMORY_USAGE}%"
    fi
fi

# Verificar uso de disco en directorio de trabajo
if command -v df >/dev/null; then
    DISK_USAGE=$(df "$HODEI_WORK_DIR" | tail -1 | awk '{print $5}' | sed 's/%//')
    if [ "$DISK_USAGE" -gt 90 ]; then
        echo "WARNING: High disk usage in work directory: ${DISK_USAGE}%"
    fi
fi

echo "OK: Worker is healthy"
exit 0