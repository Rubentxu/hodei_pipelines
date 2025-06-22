# Changelog

## [Unreleased]

### Refactor
- Reorganized `worker` module into Hexagonal Architecture (Ports & Adapters)
    - Created `domain`, `application`, `infrastructure`, and `app` submodules under `worker`
    - Moved domain models, value objects, and ports to `domain/worker/model` and `domain/worker/ports`
    - Moved use cases and application logic to `application/worker`
    - Moved infrastructure adapters and implementations to `infrastructure/worker`
    - Moved app entry point to `app/worker`
    - Updated all package declarations to match new structure
    - Renamed and removed files as needed to fit hexagonal conventions
    - Updated Gradle build scripts for new module structure

#### Breaking Change
- The package and directory structure for the worker module has changed to follow hexagonal architecture. Imports and references must be updated accordingly.
