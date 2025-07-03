#!/bin/bash
set -e

# Script para construir la imagen Docker del orchestrator

# Colores
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Configuración
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
IMAGE_NAME="hodei/orchestrator"
TAG="${1:-latest}"
FULL_IMAGE="$IMAGE_NAME:$TAG"

log_info "🐳 Building Hodei Pipelines Orchestrator Docker Image"
echo "=================================================="
echo "Project Root: $PROJECT_ROOT"
echo "Image: $FULL_IMAGE"
echo "=================================================="

# Crear Dockerfile del orchestrator
cat > "$PROJECT_ROOT/docker/orchestrator/Dockerfile" << 'EOF'
FROM openjdk:21-jdk-slim

LABEL maintainer="hodei-pipelines@rubentxu.dev"
LABEL description="Hodei Pipelines Orchestrator - Sistema central de orquestación"
LABEL version="1.0.0"

ENV HODEI_HOME=/opt/hodei
ENV HODEI_ORCHESTRATOR_HTTP_PORT=8080
ENV HODEI_ORCHESTRATOR_GRPC_PORT=9090
ENV HODEI_INFRASTRUCTURE_TYPE=docker
ENV HODEI_DOCKER_AUTO_DISCOVERY=true

RUN mkdir -p $HODEI_HOME && \
    useradd -r -u 1000 -g root -d $HODEI_HOME -s /bin/bash hodei && \
    chown -R hodei:root $HODEI_HOME

# Instalar Docker CLI para Docker-in-Docker
RUN apt-get update && apt-get install -y \
    curl \
    ca-certificates \
    gnupg \
    lsb-release && \
    curl -fsSL https://download.docker.com/linux/debian/gpg | gpg --dearmor -o /usr/share/keyrings/docker-archive-keyring.gpg && \
    echo "deb [arch=$(dpkg --print-architecture) signed-by=/usr/share/keyrings/docker-archive-keyring.gpg] https://download.docker.com/linux/debian $(lsb_release -cs) stable" | tee /etc/apt/sources.list.d/docker.list > /dev/null && \
    apt-get update && \
    apt-get install -y docker-ce-cli && \
    rm -rf /var/lib/apt/lists/*

COPY orchestrator.jar $HODEI_HOME/

USER hodei
WORKDIR $HODEI_HOME

EXPOSE $HODEI_ORCHESTRATOR_HTTP_PORT $HODEI_ORCHESTRATOR_GRPC_PORT

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:$HODEI_ORCHESTRATOR_HTTP_PORT/health || exit 1

CMD ["java", "-jar", "orchestrator.jar"]
EOF

# Crear directorio Docker del orchestrator
mkdir -p "$PROJECT_ROOT/docker/orchestrator"

# Verificar que el JAR del orchestrator existe
ORCHESTRATOR_JAR="$PROJECT_ROOT/orchestrator/build/libs/orchestrator-1.0.0.jar"
if [ ! -f "$ORCHESTRATOR_JAR" ]; then
    log_error "❌ Orchestrator JAR not found. Run 'make build-orchestrator' first."
    exit 1
fi

# Copiar JAR al contexto Docker
cp "$ORCHESTRATOR_JAR" "$PROJECT_ROOT/docker/orchestrator/orchestrator.jar"

# Construir imagen
cd "$PROJECT_ROOT/docker/orchestrator"
docker build -t "$FULL_IMAGE" .

# Limpiar
rm -f orchestrator.jar

log_info "✅ Orchestrator Docker image built: $FULL_IMAGE"