# Changelog

## [1.1.0] - 2025-07-04

### 🚀 CLI Feature Enhancement - Enterprise CLI Capabilities

**Major CLI improvements bringing Hodei closer to enterprise-grade standards**

### Added
- **🔧 Complete CRUD Operations**: Full implementation of basic resource operations
  - `hp pool create --name <name> --type <type> --max-workers <n> [--dry-run]`
  - `hp pool delete <id> [--force]` with confirmation prompts
  - `hp pool status <id>` with detailed capacity and utilization metrics
- **📋 Advanced Job Management**: Comprehensive job lifecycle management
  - `hp job submit <pipeline.kts> [--name] [--pool] [--priority] [--timeout] [--dry-run]`
  - `hp job status <id>` with timeline, progress, and execution context
  - `hp job logs <id> [--follow] [--tail N] [--since]` with real-time WebSocket streaming
  - `hp job cancel <id> [--reason] [--force]` with confirmation and reason tracking
- **🔍 Describe Commands**: kubectl-style detailed resource information
  - `hp pool describe <id> [--output json]` - Comprehensive pool information
  - `hp job describe <id> [--output json]` - Detailed job information with events
  - `hp worker describe <id> [--output json]` - Worker details with capabilities
  - `hp template describe <id> [--output json]` - Template specifications and usage
- **🏃 Shell Access Commands**: Interactive worker and job access (Phase 1 implementation)
  - `hp worker exec <id> -- <command>` - Execute commands in workers
  - `hp worker shell <id>` - Interactive shell access to workers
  - `hp job exec <id> -- <command>` - Execute commands in job contexts
  - `hp job shell <id>` - Interactive shell access to running jobs
- **✅ Dry-run Mode**: Validation without execution for all creation commands
  - Pre-execution validation for pools, jobs, and templates
  - Configuration validation and error reporting
  - Safe testing of pipeline submissions

### Enhanced
- **👷 Worker Management**: Improved worker operations with filtering
  - Enhanced `hp worker list` with pool and status filtering
  - Detailed worker status with capabilities and metadata
- **📦 Template Management**: Complete template lifecycle
  - `hp template create --name <name> --description <desc> --file <file> [--dry-run]`
  - Enhanced template listing with type filtering
  - JSON template validation and error reporting
- **📊 System Status**: Comprehensive system overview
  - Updated `hp status` with resource summaries and health metrics
  - Real-time statistics for pools, jobs, workers
  - System health indicators and guidance

### Technical Improvements
- **🔄 Error Handling**: Enhanced user feedback and authentication checks
- **📡 API Integration**: Full integration with existing REST API endpoints
- **🧪 Build System**: Verified CLI binary generation and distribution
- **📚 Documentation**: Updated CLI reference with all new features

### Progress Metrics
- **Feature Parity**: Increased from ~20% to ~60% of roadmap completion
- **Command Coverage**: Expanded from 15 to 35+ implemented commands
- **Phase 1 Completion**: All high-priority features implemented or prototyped

## [1.0.0] - 2025-07-03

### 🎉 First Production Release

**BREAKING CHANGE: Complete architecture modernization**

### Added
- **🚀 Independent CLI (`hp`)**: Enterprise-grade command-line client separated from orchestrator
  - Complete authentication system with JWT and context management
  - Resource management (pools, workers, templates, jobs)
  - Multi-environment support with saved contexts
  - Shadow JAR distribution for easy deployment
- **🔧 Bootstrap Automation**: Zero-configuration startup system
  - `BootstrapUsersService`: Automatic creation of default users (admin/admin123, user/user123, moderator/mod123)
  - `BootstrapConfiguration`: Complete system initialization (users, resource pools, templates)
  - Automatic Docker discovery and pool creation on startup
- **🔐 Production Security**: Complete authentication and authorization system
  - JWT authentication with BCrypt password hashing
  - Role-based access control (ADMIN, USER, MODERATOR)
  - Context management for multi-environment workflows
- **📚 Comprehensive Documentation**: Complete documentation suite
  - `CLI_REFERENCE_HP.md`: Complete `hp` CLI documentation with all commands and examples
  - `CLI_REFERENCE.md`: Overview with migration guide from legacy commands
  - Updated `QUICK_START_DOCKER.md` with new bootstrap workflow
  - `CLI_ROADMAP.md`: Comparison with OpenShift CLI and improvement roadmap
- **🧪 E2E Testing Infrastructure**: Complete testing system
  - `OrchestratorTestContainer`: Docker-based testing with Testcontainers
  - Integration tests for authentication, workflows, and CLI commands
  - Automated E2E test script (`scripts/test/e2e-cli-test.sh`)

### Changed
- **Architecture**: Clean separation between orchestrator (server) and CLI (client)
- **Startup**: Orchestrator now runs as pure server with automatic bootstrap
- **Authentication**: All CLI operations now require authentication
- **Docker Integration**: Automatic discovery replaces manual discovery commands

### Removed
- **Legacy CLI Commands**: Eliminated old `hodei` commands from orchestrator module
  - Removed `hodei server start`, `hodei docker discover`, etc.
  - Clean separation: orchestrator is pure server, CLI is pure client
- **Manual Setup**: No more manual user creation or resource pool setup required

### Fixed
- Compilation errors in CLI integration tests
- Authentication flow with proper JWT token management
- Docker discovery and worker provisioning
- Resource pool and template management

### Migration Guide

| Legacy Command | New Command | Notes |
|---|---|---|
| `hodei server start` | `java -jar orchestrator-all.jar` | Direct execution |
| `hodei docker discover` | N/A | Automatic on startup |
| `hodei pool list` | `hp pool list` | Requires authentication |
| `hodei health` | `hp health` | Requires authentication |

### Quick Start (v1.0.0)

```bash
# 1. Start orchestrator (auto-bootstrap)
java -jar orchestrator-all.jar

# 2. Use CLI (separate terminal)
hp login http://localhost:8080 --username admin --password admin123
hp health
hp pool list
hp job submit examples/hello-world.pipeline.kts --name my-job
```

## [Unreleased]

### Added
- Custom capability support to WorkerCapabilities builder
- Missing PipelineEvents classes (StepStarted, StepCompleted, StepFailed)
- Standalone tests for pipeline-dsl core module
- Helper function `addExtensionStep` for DSL extensions
- kotlinx-coroutines dependency to pipeline-steps-library

### Changed
- Fixed Duration syntax from `minutes(30)` to `Duration.parse("PT30M")`
- Updated deprecated channel method from `tryOffer` to `trySend`
- Fixed inline function visibility issues in pipeline-steps-library
- JobScheduler now properly verifies worker labels against job requirements

### Removed
- Duplicate steps from core DSL (dir, withEnv, timeout, retry, parallel)
- Specialized steps from core DSL (archiveArtifacts, publishTestResults, checkout, git, docker, notification)
- Obsolete SimpleKubernetesOrchestratorTest

### Fixed
- Compilation errors in pipeline-steps-library module
- ExtensionStep serialization issues
- Worker capability matching for job scheduling
- Test failures in JobSchedulerTest

### Refactor
- **Pipeline DSL Architecture** - Removed overlapping steps between core and extensions
    - Moved all specialized functionality to pipeline-steps-library extension
    - Core DSL now contains only essential building blocks
    - Extension system provides all advanced features
- Reorganized `worker` module into Hexagonal Architecture (Ports & Adapters)
    - Created `domain`, `application`, `infrastructure`, and `app` submodules under `worker`
    - Moved domain models, value objects, and ports to `domain/worker/model` and `domain/worker/ports`
    - Moved use cases and application logic to `application/worker`
    - Moved infrastructure adapters and implementations to `infrastructure/worker`
    - Moved app entry point to `app/worker`
    - Updated all package declarations to match new structure
    - Renamed and removed files as needed to fit hexagonal conventions
    - Updated Gradle build scripts for new module structure

#### Breaking Changes
- Steps like `dir`, `withEnv`, `timeout`, `retry`, `parallel` are now only available through pipeline-steps-library extension
- The package and directory structure for the worker module has changed to follow hexagonal architecture. Imports and references must be updated accordingly.
