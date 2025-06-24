# Changelog

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
