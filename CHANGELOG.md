# Changelog

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
