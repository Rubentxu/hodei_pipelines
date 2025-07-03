#!/bin/bash
set -e

# Script para construir la imagen Docker del worker de Hodei Pipelines

# Colores
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
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
DOCKER_DIR="$PROJECT_ROOT/docker/worker"
SCRIPTS_CENTRAL_DIR="$SCRIPT_DIR"
IMAGE_NAME="hodei/worker"
TAG="${1:-latest}"
FULL_IMAGE="$IMAGE_NAME:$TAG"

log_info "🐳 Building Hodei Pipelines Worker Docker Image"
echo "=================================================="
echo "Project Root: $PROJECT_ROOT"
echo "Docker Context: $DOCKER_DIR"
echo "Image: $FULL_IMAGE"
echo "=================================================="

# Verificar que estamos en el directorio correcto
if [ ! -f "$PROJECT_ROOT/settings.gradle.kts" ]; then
    log_error "❌ No se encontró settings.gradle.kts. Asegúrate de estar en la raíz del proyecto."
    exit 1
fi

# Construir el JAR del worker si no existe o está desactualizado
WORKER_JAR="$PROJECT_ROOT/orchestrator/build/libs/hodei-worker.jar"
ORCHESTRATOR_JAR="$PROJECT_ROOT/orchestrator/build/libs/orchestrator-1.0.0-all.jar"

log_info "🔨 Building worker JAR..."

# Usar la tarea personalizada workerJar que ya existe en el orchestrator
cd "$PROJECT_ROOT"
if ! ./gradlew :orchestrator:workerJar; then
    log_error "❌ Failed to build worker JAR"
    exit 1
fi

# Verificar que el JAR se construyó
if [ ! -f "$WORKER_JAR" ]; then
    log_error "❌ Worker JAR not found at $WORKER_JAR"
    exit 1
fi

log_info "✅ Worker JAR built successfully: $(du -h "$WORKER_JAR" | cut -f1)"

# Copiar el JAR al contexto de Docker
log_info "📦 Copying worker JAR to Docker context..."
cp "$WORKER_JAR" "$DOCKER_DIR/hodei-worker.jar"

# Copiar scripts centralizados
log_info "📦 Copying scripts to Docker context..."
mkdir -p "$DOCKER_DIR/scripts"
cp "$SCRIPTS_CENTRAL_DIR/entrypoint.sh" "$DOCKER_DIR/scripts/"
cp "$SCRIPTS_CENTRAL_DIR/healthcheck.sh" "$DOCKER_DIR/scripts/"

# Construir la imagen Docker
log_info "🐳 Building Docker image: $FULL_IMAGE"

cd "$DOCKER_DIR"

# Argumentos para la construcción
BUILD_ARGS=(
    --build-arg "BUILD_DATE=$(date -u +'%Y-%m-%dT%H:%M:%SZ')"
    --build-arg "VCS_REF=$(git rev-parse --short HEAD 2>/dev/null || echo 'unknown')"
    --build-arg "VERSION=$TAG"
    --tag "$FULL_IMAGE"
)

# Construir con progreso
if ! docker build "${BUILD_ARGS[@]}" .; then
    log_error "❌ Failed to build Docker image"
    exit 1
fi

# Limpiar archivos temporales
rm -f "$DOCKER_DIR/hodei-worker.jar"
rm -rf "$DOCKER_DIR/scripts"

log_info "✅ Docker image built successfully: $FULL_IMAGE"

# Mostrar información de la imagen
log_info "📋 Image information:"
docker images "$IMAGE_NAME" --format "table {{.Repository}}\t{{.Tag}}\t{{.Size}}\t{{.CreatedAt}}"

# Opcional: ejecutar tests básicos en la imagen
if [ "${RUN_TESTS:-false}" = "true" ]; then
    log_info "🧪 Running basic tests on the image..."
    
    # Test 1: Verificar que la imagen arranca
    log_info "Test 1: Image startup test"
    if docker run --rm "$FULL_IMAGE" version; then
        log_info "✅ Version test passed"
    else
        log_error "❌ Version test failed"
        exit 1
    fi
    
    # Test 2: Health check
    log_info "Test 2: Health check test"
    if docker run --rm "$FULL_IMAGE" health; then
        log_info "✅ Health check test passed"
    else
        log_warn "⚠️ Health check test failed (expected if orchestrator not running)"
    fi
    
    log_info "✅ All tests completed"
fi

# Mostrar comandos de uso
echo ""
log_info "🚀 Usage examples:"
echo ""
echo "# Run worker with local orchestrator:"
echo "docker run -d --name hodei-worker \\"
echo "  -e HODEI_ORCHESTRATOR_HOST=host.docker.internal \\"
echo "  -e WORKER_ID=my-worker-1 \\"
echo "  $FULL_IMAGE"
echo ""
echo "# Run worker with custom configuration:"
echo "docker run -d --name hodei-worker \\"
echo "  -e HODEI_ORCHESTRATOR_HOST=orchestrator.example.com \\"
echo "  -e HODEI_ORCHESTRATOR_PORT=9090 \\"
echo "  -e WORKER_LABELS='env=prod,type=docker' \\"
echo "  -e GIT_USER_NAME='CI Bot' \\"
echo "  -e GIT_USER_EMAIL='ci@example.com' \\"
echo "  -v /var/run/docker.sock:/var/run/docker.sock \\"
echo "  $FULL_IMAGE"
echo ""
echo "# Interactive shell for debugging:"
echo "docker run -it --rm $FULL_IMAGE shell"
echo ""

log_info "🎉 Build completed successfully!"