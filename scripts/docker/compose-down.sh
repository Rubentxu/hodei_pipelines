#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

# Colores
GREEN='\033[0;32m'
NC='\033[0m'

log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_info "🐳 Stopping Hodei Pipelines stack..."

cd "$PROJECT_ROOT"

if [ -f "docker-compose.yml" ]; then
    docker-compose down -v
    log_info "✅ Stack stopped successfully!"
else
    log_info "No docker-compose.yml found, stopping containers manually..."
    docker stop hodei-orchestrator hodei-worker 2>/dev/null || true
    docker rm hodei-orchestrator hodei-worker 2>/dev/null || true
fi