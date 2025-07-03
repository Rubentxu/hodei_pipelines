# Hodei Pipelines - Makefile
# Utility scripts for development and deployment

.PHONY: help build test clean run-orchestrator run-worker docker-build docker-up docker-down

# Default target
help: ## Show this help message
	@echo "Hodei Pipelines - Available commands:"
	@echo ""
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-20s\033[0m %s\n", $$1, $$2}'

# Build targets
build: ## Build the application
	@echo "🔨 Building Hodei Pipelines..."
	gradle build -q

build-worker-jar: ## Build standalone worker JAR
	@echo "🤖 Building worker JAR..."
	gradle workerJar

clean: ## Clean build artifacts
	@echo "🧹 Cleaning build artifacts..."
	gradle clean

# Test targets
test: ## Run all tests
	@echo "🧪 Running tests..."
	gradle test

test-watch: ## Run tests in watch mode (continuous)
	@echo "👀 Running tests in watch mode..."
	gradle test --continuous

# Development targets
run-orchestrator: build ## Start orchestrator server
	@echo "🚀 Starting orchestrator..."
	@./start-orchestrator.sh

run-worker: build ## Start worker (use WORKER_ID=custom-id make run-worker for custom ID)
	@echo "🤖 Starting worker..."
	@./start-worker.sh $(WORKER_ID)

run-worker-custom: build ## Start worker with custom configuration
	@echo "🤖 Starting worker with custom config..."
	@read -p "Worker ID: " worker_id; \
	read -p "Orchestrator Host (localhost): " orch_host; \
	read -p "Orchestrator Port (9090): " orch_port; \
	./start-worker.sh "$${worker_id:-worker-custom-$$$$}" "$${orch_host:-localhost}" "$${orch_port:-9090}"

# Development utilities
dev-setup: ## Setup development environment
	@echo "⚙️  Setting up development environment..."
	@chmod +x start-orchestrator.sh start-worker.sh
	@mkdir -p worker-workspace
	@echo "✅ Development environment ready"

logs-orchestrator: ## Show orchestrator logs (if running in background)
	@echo "📋 Orchestrator logs:"
	@ps aux | grep "hodei.*orchestrator" | grep -v grep || echo "Orchestrator not running"

logs-worker: ## Show worker logs (if running in background)  
	@echo "🔍 Worker logs:"
	@ps aux | grep "hodei.*worker" | grep -v grep || echo "Worker not running"

stop-all: ## Stop all running instances
	@echo "🛑 Stopping all Hodei processes..."
	@pkill -f "hodei.*orchestrator" || true
	@pkill -f "hodei.*worker" || true
	@echo "✅ All processes stopped"

# API utilities
api-health: ## Check orchestrator health
	@echo "🩺 Checking orchestrator health..."
	@curl -s http://localhost:8080/v1/health | jq . || echo "Orchestrator not responding"

api-metrics: ## Get system metrics
	@echo "📊 Getting system metrics..."
	@curl -s http://localhost:8080/v1/metrics | jq . || echo "Orchestrator not responding"

api-jobs: ## List all jobs
	@echo "📋 Listing jobs..."
	@curl -s http://localhost:8080/v1/jobs | jq . || echo "Orchestrator not responding"

# Docker targets (future)
docker-build: ## Build Docker images
	@echo "🐳 Building Docker images..."
	@echo "TODO: Implement Docker build"

docker-up: ## Start services with Docker Compose
	@echo "🐳 Starting services with Docker..."
	@echo "TODO: Implement docker-compose up"

docker-down: ## Stop Docker services
	@echo "🐳 Stopping Docker services..."
	@echo "TODO: Implement docker-compose down"

# Demo and testing
demo: build ## Run complete demo workflow
	@echo "🎬 Running demo workflow..."
	@echo "1. Starting orchestrator..."
	@./start-orchestrator.sh &
	@sleep 5
	@echo "2. Starting worker..."
	@./start-worker.sh demo-worker &
	@sleep 3
	@echo "3. Creating demo job..."
	@curl -X POST http://localhost:8080/v1/jobs -H "Content-Type: application/json" -d '{"name":"demo-job","description":"Demo job for testing"}' || echo "Failed to create job"
	@echo "4. Demo complete. Press Ctrl+C to stop services."

# Maintenance
format: ## Format code (if formatter available)
	@echo "💅 Formatting code..."
	@echo "TODO: Add ktlint or similar formatter"

lint: ## Run linter (if available)
	@echo "🔍 Running linter..."
	@echo "TODO: Add ktlint or similar linter"

# Release
tag: ## Create new version tag (use VERSION=x.y.z make tag)
	@if [ -z "$(VERSION)" ]; then \
		echo "❌ VERSION required. Usage: make tag VERSION=1.0.0"; \
		exit 1; \
	fi
	@echo "🏷️  Creating tag v$(VERSION)..."
	@git tag -a v$(VERSION) -m "Release v$(VERSION)"
	@git push origin v$(VERSION)
	@echo "✅ Tag v$(VERSION) created and pushed"

# Environment info
info: ## Show environment information
	@echo "📋 Environment Information:"
	@echo "  Java Version: $$(java -version 2>&1 | head -n 1)"
	@echo "  Gradle Version: $$(gradle --version | grep Gradle | head -n 1)"
	@echo "  Kotlin Version: $$(kotlinc -version 2>&1 | head -n 1)"
	@echo "  Current Branch: $$(git branch --show-current 2>/dev/null || echo 'Not a git repository')"
	@echo "  Project Version: $$(grep 'version = ' build.gradle.kts | cut -d'"' -f2)"