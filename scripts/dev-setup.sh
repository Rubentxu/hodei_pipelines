#!/bin/bash
set -e

# Script para configurar el entorno de desarrollo

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# Colores
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

log_info "🛠️ Setting up Hodei Pipelines development environment..."

# Verificar dependencias del sistema
log_info "🔍 Checking system dependencies..."

# Java
if command -v java >/dev/null 2>&1; then
    JAVA_VERSION=$(java -version 2>&1 | head -n1)
    log_info "✅ Java found: $JAVA_VERSION"
else
    log_warn "❌ Java not found. Please install Java 21 or later."
    exit 1
fi

# Gradle
if command -v gradle >/dev/null 2>&1; then
    GRADLE_VERSION=$(gradle --version | grep Gradle | head -n1)
    log_info "✅ Gradle found: $GRADLE_VERSION"
else
    log_warn "❌ Gradle not found. Please install Gradle."
    exit 1
fi

# Docker
if command -v docker >/dev/null 2>&1; then
    DOCKER_VERSION=$(docker --version)
    log_info "✅ Docker found: $DOCKER_VERSION"
    
    # Verificar que Docker esté corriendo
    if docker info >/dev/null 2>&1; then
        log_info "✅ Docker daemon is running"
    else
        log_warn "⚠️ Docker daemon is not running. Please start Docker."
    fi
else
    log_warn "❌ Docker not found. Please install Docker for container support."
fi

# Docker Compose
if command -v docker-compose >/dev/null 2>&1; then
    COMPOSE_VERSION=$(docker-compose --version)
    log_info "✅ Docker Compose found: $COMPOSE_VERSION"
else
    log_warn "⚠️ Docker Compose not found. Some features may not work."
fi

# Git
if command -v git >/dev/null 2>&1; then
    GIT_VERSION=$(git --version)
    log_info "✅ Git found: $GIT_VERSION"
else
    log_warn "⚠️ Git not found. Version control features may not work."
fi

# Crear directorios de trabajo
log_info "📁 Creating work directories..."
mkdir -p "$PROJECT_ROOT"/{build,logs,examples,scripts/{docker,test,demo}}

# Hacer scripts ejecutables
log_info "🔧 Making scripts executable..."
find "$PROJECT_ROOT/scripts" -name "*.sh" -exec chmod +x {} \;

# Crear archivo de configuración local
log_info "⚙️ Creating local configuration..."
cat > "$PROJECT_ROOT/.env.local" << 'EOF'
# Configuración local para desarrollo de Hodei Pipelines
# Copia este archivo a .env y personaliza según tus necesidades

# Docker Configuration
HODEI_INFRASTRUCTURE_TYPE=docker
HODEI_DOCKER_AUTO_DISCOVERY=true
DOCKER_HOST=unix:///var/run/docker.sock

# Orchestrator Configuration
ORCHESTRATOR_HTTP_PORT=8080
ORCHESTRATOR_GRPC_PORT=9090
ORCHESTRATOR_HOST=localhost

# Worker Configuration
WORKER_LABELS=env=development,type=local
WORKER_ID=dev-worker

# Logging
LOG_LEVEL=INFO
VERBOSE=true

# Development
DEV_MODE=true
AUTO_RELOAD=true
EOF

# Crear alias útiles
log_info "🔗 Creating useful aliases..."
cat > "$PROJECT_ROOT/.aliases" << 'EOF'
# Aliases útiles para desarrollo de Hodei Pipelines
alias hodei-build='make build'
alias hodei-test='make test'
alias hodei-start='make run-orchestrator-docker'
alias hodei-stop='make stop-all'
alias hodei-logs='make logs-orchestrator'
alias hodei-worker='make run-worker-docker'
alias hodei-cli='make run-cli'
alias hodei-clean='make clean'
alias hodei-help='make help'

# Para cargar los aliases, ejecuta: source .aliases
EOF

# Configurar Git hooks (si existe Git)
if command -v git >/dev/null 2>&1 && [ -d "$PROJECT_ROOT/.git" ]; then
    log_info "🪝 Setting up Git hooks..."
    
    # Pre-commit hook
    cat > "$PROJECT_ROOT/.git/hooks/pre-commit" << 'EOF'
#!/bin/bash
echo "🔍 Running pre-commit checks..."

# Verificar que el código compila
if ! make build >/dev/null 2>&1; then
    echo "❌ Build failed. Please fix compilation errors before committing."
    exit 1
fi

# Ejecutar tests unitarios rápidos
if ! make test-unit >/dev/null 2>&1; then
    echo "❌ Unit tests failed. Please fix tests before committing."
    exit 1
fi

echo "✅ Pre-commit checks passed!"
EOF
    chmod +x "$PROJECT_ROOT/.git/hooks/pre-commit"
fi

# Configuración del IDE (VS Code)
if command -v code >/dev/null 2>&1; then
    log_info "💻 Setting up VS Code configuration..."
    mkdir -p "$PROJECT_ROOT/.vscode"
    
    # Settings para VS Code
    cat > "$PROJECT_ROOT/.vscode/settings.json" << 'EOF'
{
    "java.configuration.updateBuildConfiguration": "automatic",
    "java.compile.nullAnalysis.mode": "automatic",
    "gradle.nestedProjects": true,
    "files.exclude": {
        "**/build/": true,
        "**/.gradle/": true
    },
    "search.exclude": {
        "**/build/": true,
        "**/.gradle/": true
    }
}
EOF

    # Tareas para VS Code
    cat > "$PROJECT_ROOT/.vscode/tasks.json" << 'EOF'
{
    "version": "2.0.0",
    "tasks": [
        {
            "label": "Build Project",
            "type": "shell", 
            "command": "make build",
            "group": "build",
            "presentation": {
                "echo": true,
                "reveal": "always",
                "focus": false,
                "panel": "shared"
            }
        },
        {
            "label": "Run Tests",
            "type": "shell",
            "command": "make test",
            "group": "test"
        },
        {
            "label": "Start Orchestrator",
            "type": "shell",
            "command": "make run-orchestrator-docker",
            "group": "build"
        }
    ]
}
EOF
fi

# Resumen final
echo ""
log_info "🎉 Development environment setup completed!"
echo "=============================================="
echo ""
echo -e "${BLUE}📋 Next steps:${NC}"
echo "1. Source aliases: source .aliases"
echo "2. Copy environment: cp .env.local .env"
echo "3. Build project: make build"
echo "4. Run tests: make test"
echo "5. Start system: make docker-compose-up"
echo ""
echo -e "${BLUE}🚀 Quick start commands:${NC}"
echo "  make help                    # Show all available commands"
echo "  make build                   # Build all modules"
echo "  make docker-build-all        # Build Docker images"
echo "  make run-orchestrator-docker # Start orchestrator"
echo "  make example-pipeline        # Run example pipeline"
echo ""
echo -e "${BLUE}🔧 Development workflow:${NC}"
echo "  1. Edit code"
echo "  2. make build (builds and tests)"
echo "  3. make docker-build-worker (rebuild worker image)"
echo "  4. make run-worker-docker (restart worker)"
echo "  5. make example-remote-pipeline (test changes)"
echo ""
echo "Happy coding! 🚀"