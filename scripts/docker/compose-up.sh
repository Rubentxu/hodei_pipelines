#!/bin/bash
set -e

# Script para levantar el stack completo con Docker Compose

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

# Colores
GREEN='\033[0;32m'
NC='\033[0m'

log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_info "🐳 Starting Hodei Pipelines complete stack..."

# Crear docker-compose.yml
cat > "$PROJECT_ROOT/docker-compose.yml" << 'EOF'
version: '3.8'

services:
  orchestrator:
    image: hodei/orchestrator:latest
    container_name: hodei-orchestrator
    ports:
      - "8080:8080"
      - "9090:9090"
    environment:
      - HODEI_INFRASTRUCTURE_TYPE=docker
      - HODEI_DOCKER_AUTO_DISCOVERY=true
    volumes:
      - /var/run/docker.sock:/var/run/docker.sock
    networks:
      - hodei-network
    restart: unless-stopped
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/health"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 60s

  worker:
    image: hodei/worker:latest
    container_name: hodei-worker
    depends_on:
      orchestrator:
        condition: service_healthy
    environment:
      - HODEI_ORCHESTRATOR_HOST=orchestrator
      - HODEI_ORCHESTRATOR_PORT=9090
      - WORKER_LABELS=env=docker-compose,type=docker
    volumes:
      - /var/run/docker.sock:/var/run/docker.sock
    networks:
      - hodei-network
    restart: unless-stopped

networks:
  hodei-network:
    driver: bridge
EOF

# Ejecutar docker-compose
cd "$PROJECT_ROOT"
docker-compose up -d

log_info "✅ Stack started successfully!"
echo ""
echo "🌐 Services available at:"
echo "  📊 Orchestrator API: http://localhost:8080"
echo "  🔌 gRPC API: localhost:9090"
echo ""
echo "📋 Useful commands:"
echo "  make logs-orchestrator  # Ver logs del orchestrator"
echo "  make logs-worker        # Ver logs del worker"
echo "  make stop-all          # Parar todos los servicios"
echo "  docker-compose logs -f # Ver todos los logs"