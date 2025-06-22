# Changelog

All notable changes to the Hodei Pipelines project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- **Integration Testing Infrastructure**
  - Embedded gRPC server for comprehensive worker testing
  - Mock services for JobExecutor and WorkerManagement
  - Test artifact and job builders for realistic test scenarios
  - Complete integration tests validating server-worker communication
  - Worker registration, heartbeat, and lifecycle testing
  - Artifact transfer and cache query simulation
- **Test Infrastructure Components**
  - `EmbeddedGrpcServer` for controlled test environments
  - `MockJobExecutorService` simulating server-side job execution
  - `MockWorkerManagementService` for worker lifecycle testing
  - `TestArtifactBuilder` and `TestJobBuilder` for test data creation
  - `BasicIntegrationTest` with 100% success rate (6 tests)
  - `MinimalIntegrationTest` for core functionality validation

### Changed
- Enhanced test dependencies with Mockito for integration testing
- Centralized dependency management for testing libraries
- Resolved complex mocking issues with value classes and timing

### Technical Details
- Files added: `EmbeddedGrpcServer.kt`, `MockJobExecutorService.kt`, `MockWorkerManagementService.kt`
- Integration test suites: `BasicIntegrationTest.kt` (fully working), `MinimalIntegrationTest.kt`
- Enhanced worker testing capabilities with realistic gRPC communication
- Comprehensive testing of worker-server bidirectional communication
- Foundation for Phase 2 artifact transfer and caching validation

### Deprecated

### Removed

### Fixed

### Security

## [0.3.0] - 2025-06-21

### Added
- **Phase 2 Artifact Transfer System**
  - Intelligent artifact caching with persistent metadata storage
  - GZIP compression/decompression support (60-80% bandwidth reduction)
  - Cache query protocol to avoid redundant transfers
  - Enhanced protobuf definitions for cache management
  - Comprehensive test coverage for Phase 2 functionality
- **Performance Improvements**
  - Cache hit detection and optimization
  - Compression metrics logging
  - Optimized transfer protocols
- **Worker Cache Management**
  - Persistent artifact storage with disk-based metadata
  - Cache status reporting and metrics
  - Artifact version tracking for cache validity

### Changed
- Enhanced `ArtifactChunk` message with compression metadata
- Improved `ArtifactAck` with cache hit status and metrics
- Extended worker-server protocol with cache query/response messages
- Updated artifact transfer tests with Phase 2 scenarios

### Technical Details
- Files modified: `server_worker.proto`, `JobExecutorServiceImpl.kt`, `PipelineWorker.kt`, `ArtifactTransferTest.kt`
- New protocol messages: `ArtifactCacheQuery`, `ArtifactCacheResponse`, `ArtifactCacheInfo`, `ArtifactCacheStatus`
- Added compression types: `COMPRESSION_GZIP`, `COMPRESSION_ZSTD` (GZIP implemented)

## [0.2.0] - 2025-06-21

### Added
- **Phase 1 Artifact Transfer System**
  - Basic artifact chunked transfer (64KB chunks)
  - SHA-256 checksum validation for integrity
  - Artifact metadata and type definitions
  - Protocol buffer definitions for artifact transfer
- **Enhanced Worker-Server Communication**
  - Bidirectional artifact transfer capabilities
  - Artifact acknowledgment system
  - Resource usage monitoring (CPU, memory, network, disk)
  - Enhanced worker session management
- **Security and RBAC**
  - Role-Based Access Control implementation
  - Security policies for job execution
  - Enhanced worker registration with capabilities

### Changed
- Removed redundant `SendHeartbeat` RPC from worker management
- Enhanced gRPC protocol with artifact transfer messages
- Improved worker heartbeat system with resource metrics

### Technical Details
- New artifact types: `LIBRARY`, `DATASET`, `CONFIG`, `RESOURCE`, `DOCKER_IMAGE`, `ARCHIVE`
- Enhanced protobuf messages: `Artifact`, `ArtifactChunk`, `ArtifactAck`
- Added compression type enum (preparation for Phase 2)

## [0.1.0] - 2025-06-20

### Added
- **Initial Project Setup**
  - Hexagonal architecture implementation
  - Kotlin multi-module project structure
  - gRPC-based worker-server communication
- **Core Domain**
  - Job execution domain model
  - Worker management system
  - Pipeline script execution with Kotlin DSL
- **Infrastructure**
  - In-memory implementations for MVP
  - Basic job scheduling and orchestration
  - Simple Kubernetes orchestrator simulation
- **Testing Framework**
  - BDD-style test structure
  - Comprehensive unit test coverage
  - Integration test setup

### Technical Details
- Gradle multi-module build system
- Protocol Buffers for communication
- Kotlin Script Host for pipeline execution
- Coroutine-based asynchronous processing

---

## Legend

- **Added**: New features
- **Changed**: Changes in existing functionality  
- **Deprecated**: Soon-to-be removed features
- **Removed**: Removed features
- **Fixed**: Bug fixes
- **Security**: Security improvements

## Version Format

This project uses [Semantic Versioning](https://semver.org/):
- **MAJOR** version when you make incompatible API changes
- **MINOR** version when you add functionality in a backwards compatible manner
- **PATCH** version when you make backwards compatible bug fixes