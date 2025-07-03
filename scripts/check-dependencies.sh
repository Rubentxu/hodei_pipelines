#!/bin/bash

# Script para verificar dependencias del sistema

# Colores
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BLUE='\033[0;34m'
NC='\033[0m'

log_info() {
    echo -e "${GREEN}[✅]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[⚠️]${NC} $1"
}

log_error() {
    echo -e "${RED}[❌]${NC} $1"
}

log_header() {
    echo -e "${BLUE}$1${NC}"
}

echo ""
log_header "🔍 Hodei Pipelines - System Dependencies Check"
log_header "=============================================="

# Variables de estado
ALL_OK=true

# Java
log_header "\n📦 Java Runtime Environment"
if command -v java >/dev/null 2>&1; then
    JAVA_VERSION=$(java -version 2>&1 | head -n1 | cut -d'"' -f2)
    JAVA_MAJOR=$(echo "$JAVA_VERSION" | cut -d'.' -f1)
    
    if [ "$JAVA_MAJOR" -ge 21 ]; then
        log_info "Java $JAVA_VERSION (✅ Compatible)"
    else
        log_warn "Java $JAVA_VERSION (⚠️ Requires Java 21+)"
        ALL_OK=false
    fi
else
    log_error "Java not found (❌ Required)"
    ALL_OK=false
fi

# Gradle
log_header "\n🔨 Build System"
if command -v gradle >/dev/null 2>&1; then
    GRADLE_VERSION=$(gradle --version 2>/dev/null | grep "Gradle" | head -n1 | awk '{print $2}')
    log_info "Gradle $GRADLE_VERSION"
else
    log_error "Gradle not found (❌ Required for building)"
    ALL_OK=false
fi

# Docker
log_header "\n🐳 Container Runtime"
if command -v docker >/dev/null 2>&1; then
    DOCKER_VERSION=$(docker --version | awk '{print $3}' | sed 's/,//')
    log_info "Docker $DOCKER_VERSION"
    
    # Verificar que Docker daemon esté corriendo
    if docker info >/dev/null 2>&1; then
        log_info "Docker daemon is running"
        
        # Verificar permisos
        if docker ps >/dev/null 2>&1; then
            log_info "Docker permissions OK"
        else
            log_warn "Docker permissions issue (may need sudo or user in docker group)"
        fi
    else
        log_warn "Docker daemon not running"
        ALL_OK=false
    fi
else
    log_error "Docker not found (❌ Required for containers)"
    ALL_OK=false
fi

# Docker Compose
if command -v docker-compose >/dev/null 2>&1; then
    COMPOSE_VERSION=$(docker-compose --version | awk '{print $3}' | sed 's/,//')
    log_info "Docker Compose $COMPOSE_VERSION"
elif docker compose version >/dev/null 2>&1; then
    COMPOSE_VERSION=$(docker compose version --short)
    log_info "Docker Compose (plugin) $COMPOSE_VERSION"
else
    log_warn "Docker Compose not found (⚠️ Recommended for stack management)"
fi

# Git
log_header "\n📝 Version Control"
if command -v git >/dev/null 2>&1; then
    GIT_VERSION=$(git --version | awk '{print $3}')
    log_info "Git $GIT_VERSION"
else
    log_warn "Git not found (⚠️ Recommended for development)"
fi

# curl/wget
log_header "\n🌐 HTTP Tools"
if command -v curl >/dev/null 2>&1; then
    CURL_VERSION=$(curl --version | head -n1 | awk '{print $2}')
    log_info "curl $CURL_VERSION"
elif command -v wget >/dev/null 2>&1; then
    WGET_VERSION=$(wget --version | head -n1 | awk '{print $3}')
    log_info "wget $WGET_VERSION"
else
    log_warn "curl or wget not found (⚠️ Needed for API testing)"
fi

# jq
if command -v jq >/dev/null 2>&1; then
    JQ_VERSION=$(jq --version | sed 's/jq-//')
    log_info "jq $JQ_VERSION (for JSON parsing)"
else
    log_warn "jq not found (⚠️ Helpful for API responses)"
fi

# Verificar puertos disponibles
log_header "\n🔌 Port Availability"
check_port() {
    local port=$1
    local service=$2
    
    if netstat -tuln 2>/dev/null | grep ":$port " >/dev/null; then
        log_warn "Port $port in use (needed for $service)"
        return 1
    else
        log_info "Port $port available (for $service)"
        return 0
    fi
}

check_port 8080 "Orchestrator HTTP API"
check_port 9090 "Orchestrator gRPC API"

# Verificar recursos del sistema
log_header "\n💻 System Resources"

# Memoria
if command -v free >/dev/null 2>&1; then
    TOTAL_MEM=$(free -m | awk 'NR==2{print $2}')
    if [ "$TOTAL_MEM" -ge 4096 ]; then
        log_info "Memory: ${TOTAL_MEM}MB (✅ Sufficient)"
    else
        log_warn "Memory: ${TOTAL_MEM}MB (⚠️ Recommended: 4GB+)"
    fi
elif command -v vm_stat >/dev/null 2>&1; then
    # macOS
    TOTAL_MEM=$(($(sysctl -n hw.memsize) / 1024 / 1024))
    log_info "Memory: ${TOTAL_MEM}MB"
fi

# Espacio en disco
AVAILABLE_SPACE=$(df . | awk 'NR==2 {print $4}')
if [ "$AVAILABLE_SPACE" -ge 5000000 ]; then # 5GB en KB
    log_info "Disk space: Available (✅ Sufficient)"
else
    log_warn "Disk space: Limited (⚠️ May need cleanup)"
fi

# CPU cores
if command -v nproc >/dev/null 2>&1; then
    CPU_CORES=$(nproc)
    log_info "CPU cores: $CPU_CORES"
elif command -v sysctl >/dev/null 2>&1; then
    # macOS
    CPU_CORES=$(sysctl -n hw.ncpu)
    log_info "CPU cores: $CPU_CORES"
fi

# Resumen final
log_header "\n📋 Summary"
echo "=============================================="

if [ "$ALL_OK" = true ]; then
    log_info "🎉 All required dependencies are installed!"
    echo ""
    echo "You can now:"
    echo "  1. Build the project: make build"
    echo "  2. Run tests: make test"  
    echo "  3. Start the system: make docker-compose-up"
    echo "  4. Run examples: make example-pipeline"
else
    log_error "❌ Some required dependencies are missing"
    echo ""
    echo "Please install the missing dependencies and run this check again."
    echo ""
    echo "Installation guides:"
    echo "  Java 21: https://adoptium.net/"
    echo "  Gradle: https://gradle.org/install/"
    echo "  Docker: https://docs.docker.com/get-docker/"
    exit 1
fi

echo ""