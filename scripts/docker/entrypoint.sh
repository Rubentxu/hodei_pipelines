#!/bin/bash
set -e

# Colores para logs
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

log() {
    echo -e "${BLUE}[$(date +'%Y-%m-%d %H:%M:%S')]${NC} $1"
}

log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Función para generar Worker ID si no se proporciona
generate_worker_id() {
    if [ -z "$WORKER_ID" ]; then
        WORKER_ID="docker-worker-$(hostname)-$(date +%s)"
        export WORKER_ID
        log_info "Generated Worker ID: $WORKER_ID"
    else
        log_info "Using provided Worker ID: $WORKER_ID"
    fi
}

# Función para verificar conectividad con el orquestador
check_orchestrator_connectivity() {
    log_info "Checking connectivity to orchestrator at $HODEI_ORCHESTRATOR_HOST:$HODEI_ORCHESTRATOR_PORT"
    
    local retries=5
    local delay=5
    
    for i in $(seq 1 $retries); do
        if timeout 10 bash -c "</dev/tcp/$HODEI_ORCHESTRATOR_HOST/$HODEI_ORCHESTRATOR_PORT" 2>/dev/null; then
            log_info "✅ Successfully connected to orchestrator"
            return 0
        else
            log_warn "❌ Cannot connect to orchestrator (attempt $i/$retries)"
            if [ $i -lt $retries ]; then
                log_info "Retrying in ${delay}s..."
                sleep $delay
            fi
        fi
    done
    
    log_error "❌ Failed to connect to orchestrator after $retries attempts"
    return 1
}

# Función para configurar el entorno de trabajo
setup_work_environment() {
    log_info "Setting up work environment"
    
    # Crear directorios de trabajo
    mkdir -p $HODEI_WORK_DIR/{tmp,cache,artifacts,logs}
    
    # Configurar Git si hay credenciales
    if [ ! -z "$GIT_USER_NAME" ] && [ ! -z "$GIT_USER_EMAIL" ]; then
        git config --global user.name "$GIT_USER_NAME"
        git config --global user.email "$GIT_USER_EMAIL"
        log_info "Git configured with user: $GIT_USER_NAME <$GIT_USER_EMAIL>"
    fi
    
    # Configurar SSH si hay claves
    if [ ! -z "$SSH_PRIVATE_KEY" ]; then
        mkdir -p ~/.ssh
        echo "$SSH_PRIVATE_KEY" > ~/.ssh/id_rsa
        chmod 600 ~/.ssh/id_rsa
        ssh-keyscan -H github.com gitlab.com bitbucket.org >> ~/.ssh/known_hosts 2>/dev/null || true
        log_info "SSH key configured"
    fi
    
    # Verificar herramientas disponibles
    log_info "Available tools:"
    command -v java >/dev/null && echo "  ✅ Java $(java -version 2>&1 | head -n1)"
    command -v git >/dev/null && echo "  ✅ Git $(git --version)"
    command -v docker >/dev/null && echo "  ✅ Docker $(docker --version)"
    command -v node >/dev/null && echo "  ✅ Node.js $(node --version)"
    command -v python3 >/dev/null && echo "  ✅ Python $(python3 --version)"
}

# Función para mostrar información del worker
show_worker_info() {
    log_info "🚀 Hodei Pipelines Worker Starting"
    echo "=================================================="
    echo "Worker ID: $WORKER_ID"
    echo "Orchestrator: $HODEI_ORCHESTRATOR_HOST:$HODEI_ORCHESTRATOR_PORT"
    echo "Work Directory: $HODEI_WORK_DIR"
    echo "Worker Labels: ${WORKER_LABELS:-none}"
    echo "Container: $(hostname)"
    echo "Platform: $(uname -a)"
    echo "Java: $(java -version 2>&1 | head -n1)"
    echo "=================================================="
}

# Función principal para iniciar el worker
start_worker() {
    log_info "Starting Hodei Pipelines Worker..."
    
    # Construir argumentos para el worker
    local args=(
        "--mode=worker"
        "--worker-id=$WORKER_ID"
        "--orchestrator-host=$HODEI_ORCHESTRATOR_HOST"
        "--orchestrator-port=$HODEI_ORCHESTRATOR_PORT"
        "--work-dir=$HODEI_WORK_DIR"
    )
    
    # Agregar labels si están definidos
    if [ ! -z "$WORKER_LABELS" ]; then
        args+=("--labels=$WORKER_LABELS")
    fi
    
    # Agregar nivel de log
    args+=("--log-level=$LOG_LEVEL")
    
    log_info "Executing: java -jar $HODEI_HOME/hodei-worker.jar ${args[*]}"
    
    # Ejecutar el worker
    exec java \
        -Xmx${JAVA_MAX_HEAP:-1g} \
        -Xms${JAVA_MIN_HEAP:-256m} \
        -XX:+UseG1GC \
        -XX:+UseContainerSupport \
        -Djava.security.egd=file:/dev/./urandom \
        -Dfile.encoding=UTF-8 \
        -jar $HODEI_HOME/hodei-worker.jar \
        "${args[@]}"
}

# Función de manejo de señales
cleanup() {
    log_info "🛑 Received shutdown signal, cleaning up..."
    
    # Aquí se pueden agregar tareas de limpieza
    # Por ejemplo, cancelar jobs en ejecución, limpiar archivos temporales, etc.
    
    log_info "✅ Cleanup completed"
    exit 0
}

# Configurar manejo de señales
trap cleanup SIGTERM SIGINT

# Función principal
main() {
    case "${1:-worker}" in
        "worker")
            generate_worker_id
            setup_work_environment
            check_orchestrator_connectivity || exit 1
            show_worker_info
            start_worker
            ;;
        "health")
            # Comando para health check
            $HODEI_HOME/scripts/healthcheck.sh
            ;;
        "version")
            echo "Hodei Pipelines Worker 1.0.0"
            java -version
            ;;
        "shell"|"bash")
            log_info "Starting interactive shell"
            exec /bin/bash
            ;;
        *)
            log_error "Unknown command: $1"
            echo "Available commands: worker, health, version, shell"
            exit 1
            ;;
    esac
}

# Ejecutar función principal con todos los argumentos
main "$@"