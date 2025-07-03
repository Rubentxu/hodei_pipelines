# Makefile para Hodei Pipelines
# Automatización de construcción, testing y despliegue

.PHONY: help build test clean docker run-orchestrator run-worker run-cli dev-setup e2e-test

# Variables de configuración
PROJECT_NAME := hodei-pipelines
VERSION := $(shell git describe --tags --always --dirty 2>/dev/null || echo "1.0.0-dev")
BUILD_TIME := $(shell date -u +'%Y-%m-%dT%H:%M:%SZ')
GIT_COMMIT := $(shell git rev-parse --short HEAD 2>/dev/null || echo "unknown")

# Configuración Docker
DOCKER_REGISTRY := 
DOCKER_NAMESPACE := hodei
ORCHESTRATOR_IMAGE := $(DOCKER_NAMESPACE)/orchestrator
WORKER_IMAGE := $(DOCKER_NAMESPACE)/worker
CLI_IMAGE := $(DOCKER_NAMESPACE)/cli

# Puertos por defecto
ORCHESTRATOR_HTTP_PORT := 8080
ORCHESTRATOR_GRPC_PORT := 9090
WORKER_PORT := 9091

# Directorio de trabajo
WORK_DIR := $(PWD)
SCRIPTS_DIR := $(WORK_DIR)/scripts
BUILD_DIR := $(WORK_DIR)/build
DOCKER_DIR := $(WORK_DIR)/docker

# Colores para output
GREEN := \033[0;32m
YELLOW := \033[1;33m
RED := \033[0;31m
BLUE := \033[0;34m
NC := \033[0m

# Función para mostrar mensajes
define log_info
	@echo -e "$(GREEN)[INFO]$(NC) $(1)"
endef

define log_warn
	@echo -e "$(YELLOW)[WARN]$(NC) $(1)"
endef

define log_error
	@echo -e "$(RED)[ERROR]$(NC) $(1)"
endef

## help: Mostrar esta ayuda
help:
	@echo "$(GREEN)🚀 Hodei Pipelines - Makefile$(NC)"
	@echo "=================================================="
	@echo "$(BLUE)Comandos disponibles:$(NC)"
	@echo ""
	@sed -n 's/^##//p' $(MAKEFILE_LIST) | column -t -s ':' | sed -e 's/^/ /'
	@echo ""
	@echo "$(BLUE)Variables de entorno útiles:$(NC)"
	@echo "  VERSION=x.y.z          - Versión para tags de Docker"
	@echo "  DOCKER_REGISTRY=url    - Registry de Docker personalizado"
	@echo "  ORCHESTRATOR_HOST=ip   - Host del orquestador para workers"
	@echo ""

## build: Construir todos los módulos del proyecto
build:
	$(call log_info,"🔨 Building all project modules...")
	@gradle build -x test
	$(call log_info,"✅ Build completed successfully")

## build-orchestrator: Construir solo el módulo orchestrator
build-orchestrator:
	$(call log_info,"🔨 Building orchestrator module...")
	@gradle :orchestrator:build -x test
	$(call log_info,"✅ Orchestrator build completed")

## build-worker: Construir JAR del worker
build-worker:
	$(call log_info,"🔨 Building worker JAR...")
	@gradle :orchestrator:workerJar
	$(call log_info,"✅ Worker JAR built: orchestrator/build/libs/hodei-worker.jar")

## build-cli: Construir CLI ejecutable
build-cli:
	$(call log_info,"🔨 Building CLI executable...")
	@gradle :pipeline-dsl:pipeline-cli:shadowJar
	$(call log_info,"✅ CLI built: pipeline-dsl/pipeline-cli/build/libs/pipeline-cli.jar")

## test: Ejecutar todos los tests
test:
	$(call log_info,"🧪 Running tests...")
	@gradle test
	$(call log_info,"✅ Tests completed")

## test-unit: Ejecutar solo tests unitarios
test-unit:
	$(call log_info,"🧪 Running unit tests...")
	@gradle test --tests "*Test" --exclude-task=":*:integrationTest"

## test-integration: Ejecutar tests de integración
test-integration:
	$(call log_info,"🧪 Running integration tests...")
	@gradle test --tests "*IntegrationTest"

## clean: Limpiar archivos de construcción
clean:
	$(call log_info,"🧹 Cleaning build artifacts...")
	@gradle clean
	@docker system prune -f 2>/dev/null || true
	@rm -rf $(BUILD_DIR)
	$(call log_info,"✅ Clean completed")

## docker-build-all: Construir todas las imágenes Docker
docker-build-all: docker-build-orchestrator docker-build-worker docker-build-cli

## docker-build-orchestrator: Construir imagen Docker del orchestrator
docker-build-orchestrator: build-orchestrator
	$(call log_info,"🐳 Building orchestrator Docker image...")
	@bash $(SCRIPTS_DIR)/docker/build-orchestrator.sh $(VERSION)
	$(call log_info,"✅ Orchestrator image built: $(ORCHESTRATOR_IMAGE):$(VERSION)")

## docker-build-worker: Construir imagen Docker del worker
docker-build-worker: build-worker
	$(call log_info,"🐳 Building worker Docker image...")
	@chmod +x $(SCRIPTS_DIR)/docker/build-image.sh
	@bash $(SCRIPTS_DIR)/docker/build-image.sh $(VERSION)
	$(call log_info,"✅ Worker image built: $(WORKER_IMAGE):$(VERSION)")

## docker-build-cli: Construir imagen Docker del CLI
docker-build-cli: build-cli
	$(call log_info,"🐳 Building CLI Docker image...")
	@bash $(SCRIPTS_DIR)/docker/build-cli.sh $(VERSION)
	$(call log_info,"✅ CLI image built: $(CLI_IMAGE):$(VERSION)")

## dev-setup: Configurar entorno de desarrollo
dev-setup:
	$(call log_info,"🛠️ Setting up development environment...")
	@bash $(SCRIPTS_DIR)/dev-setup.sh
	$(call log_info,"✅ Development environment ready")

## run-orchestrator: Ejecutar orchestrator localmente
run-orchestrator: build-orchestrator
	$(call log_info,"🚀 Starting orchestrator...")
	@echo "Orchestrator will be available at:"
	@echo "  HTTP API: http://localhost:$(ORCHESTRATOR_HTTP_PORT)"
	@echo "  gRPC API: localhost:$(ORCHESTRATOR_GRPC_PORT)"
	@echo "Press Ctrl+C to stop"
	@java -jar orchestrator/build/libs/orchestrator-1.0.0.jar

## run-orchestrator-docker: Ejecutar orchestrator en Docker con auto-discovery de Docker local
run-orchestrator-docker: docker-build-orchestrator
	$(call log_info,"🐳 Starting orchestrator in Docker with Docker auto-discovery...")
	@docker run -d --name hodei-orchestrator \
		-p $(ORCHESTRATOR_HTTP_PORT):8080 \
		-p $(ORCHESTRATOR_GRPC_PORT):9090 \
		-v /var/run/docker.sock:/var/run/docker.sock \
		-e HODEI_INFRASTRUCTURE_TYPE=docker \
		-e HODEI_DOCKER_AUTO_DISCOVERY=true \
		$(ORCHESTRATOR_IMAGE):$(VERSION)
	@echo "Orchestrator started at:"
	@echo "  HTTP API: http://localhost:$(ORCHESTRATOR_HTTP_PORT)"
	@echo "  gRPC API: localhost:$(ORCHESTRATOR_GRPC_PORT)"
	@echo "  Logs: docker logs -f hodei-orchestrator"

## run-worker-docker: Ejecutar worker en Docker
run-worker-docker: docker-build-worker
	$(call log_info,"🐳 Starting worker in Docker...")
	@docker run -d --name hodei-worker \
		-e HODEI_ORCHESTRATOR_HOST=${ORCHESTRATOR_HOST:-host.docker.internal} \
		-e HODEI_ORCHESTRATOR_PORT=$(ORCHESTRATOR_GRPC_PORT) \
		-e WORKER_LABELS="env=local,type=docker" \
		-v /var/run/docker.sock:/var/run/docker.sock \
		$(WORKER_IMAGE):$(VERSION)
	@echo "Worker started and connected to orchestrator"
	@echo "  Logs: docker logs -f hodei-worker"

## run-cli: Ejecutar CLI
run-cli: build-cli
	$(call log_info,"💻 Running CLI...")
	@java -jar pipeline-dsl/pipeline-cli/build/libs/pipeline-cli.jar $(ARGS)

## docker-compose-up: Levantar stack completo con Docker Compose
docker-compose-up: docker-build-all
	$(call log_info,"🐳 Starting complete stack with Docker Compose...")
	@bash $(SCRIPTS_DIR)/docker/compose-up.sh

## docker-compose-down: Bajar stack de Docker Compose
docker-compose-down:
	$(call log_info,"🐳 Stopping Docker Compose stack...")
	@bash $(SCRIPTS_DIR)/docker/compose-down.sh

## e2e-test: Ejecutar tests end-to-end completos
e2e-test: docker-build-all
	$(call log_info,"🧪 Running end-to-end tests...")
	@bash $(SCRIPTS_DIR)/test/e2e-test.sh
	$(call log_info,"✅ E2E tests completed")

## demo: Ejecutar demo completo del sistema
demo: docker-compose-up
	$(call log_info,"🎬 Running complete system demo...")
	@bash $(SCRIPTS_DIR)/demo/run-demo.sh
	$(call log_info,"✅ Demo completed")

## logs-orchestrator: Mostrar logs del orchestrator
logs-orchestrator:
	@docker logs -f hodei-orchestrator 2>/dev/null || echo "Orchestrator container not running"

## logs-worker: Mostrar logs del worker
logs-worker:
	@docker logs -f hodei-worker 2>/dev/null || echo "Worker container not running"

## stop-all: Parar todos los contenedores
stop-all:
	$(call log_info,"🛑 Stopping all containers...")
	@docker stop hodei-orchestrator hodei-worker 2>/dev/null || true
	@docker rm hodei-orchestrator hodei-worker 2>/dev/null || true
	$(call log_info,"✅ All containers stopped")

## install-cli: Instalar CLI globalmente
install-cli: build-cli
	$(call log_info,"📦 Installing CLI globally...")
	@bash $(SCRIPTS_DIR)/install-cli.sh

## release: Crear release completo con tags
release: clean test docker-build-all
	$(call log_info,"🚀 Creating release $(VERSION)...")
	@bash $(SCRIPTS_DIR)/release.sh $(VERSION)
	$(call log_info,"✅ Release $(VERSION) created")

## check-dependencies: Verificar dependencias del sistema
check-dependencies:
	$(call log_info,"🔍 Checking system dependencies...")
	@bash $(SCRIPTS_DIR)/check-dependencies.sh

## example-pipeline: Ejecutar pipeline de ejemplo
example-pipeline: run-cli
	$(call log_info,"📋 Running example pipeline...")
	@make run-cli ARGS="execute examples/hello-world.pipeline.kts --verbose"

## example-remote-pipeline: Ejecutar pipeline de ejemplo en modo remoto
example-remote-pipeline: build-cli
	$(call log_info,"🌐 Running example pipeline remotely...")
	@make run-cli ARGS="execute examples/hello-world.pipeline.kts --orchestrator http://localhost:$(ORCHESTRATOR_HTTP_PORT) --follow --verbose"

# Target por defecto
.DEFAULT_GOAL := help

# Configuraciones especiales
.SILENT: help
.ONESHELL: docker-compose-up docker-compose-down