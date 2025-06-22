# Diagramas de Flujo Detallados - Hodei Pipelines

## 1. Flujo Completo de Creación y Ejecución de Job

```mermaid
flowchart TD
    A[CreateAndExecuteJobRequest] --> B[CreateAndExecuteJobUseCase]
    B --> C[JobDefinition.validate]
    C --> D[Job.create with JobId.generate]
    D --> E[JobRepository.save Job]
    E --> F[EventPublisher.publishJobEvent JobDomainEvent.JobCreated]
    F --> G[WorkerRepository.findAvailableWorkers]
    G --> H{availableWorkers.isEmpty?}
    H -->|Yes| I[Job.fail - No Workers]
    H -->|No| J[Worker.assignJob]
    J --> K[WorkerRepository.save busyWorker]
    K --> L[Job.start]
    L --> M[JobRepository.save runningJob]
    M --> N[EventPublisher.publishJobEvent JobDomainEvent.JobStarted]
    N --> O[JobExecutor.execute]
    O --> P[PipelineScriptExecutor.execute]
    P --> Q[ExecutionStrategyManager.getStrategy]
    Q --> R[JobExecutionStrategy.execute]
    R --> S{JobExecutionEvent}
    S -->|Completed| T[Job.complete with exitCode]
    S -->|Failed| U[Job.fail with error]
    S -->|OutputReceived| V[JobExecutionResult.JobOutput]
    T --> W[Worker.completeJob]
    U --> X[Worker.completeJob] 
    W --> Y[EventPublisher.publishJobEvent JobDomainEvent.JobCompleted]
    X --> Z[EventPublisher.publishJobEvent JobDomainEvent.JobFailed]
    
    style B fill:#e1f5fe
    style D fill:#f3e5f5
    style J fill:#e8f5e8
    style P fill:#fff3e0
    style R fill:#fce4ec
```

## 2. Flujo de Registro de Worker

```mermaid
flowchart TD
    A[WorkerRegistrationRequest] --> B[WorkerManagementServiceImpl.registerWorker]
    B --> C[RegisterWorkerUseCase.execute]
    C --> D[WorkerRegistrationRequest.toDomain]
    D --> E[WorkerCapabilities.builder.build]
    E --> F[Worker.create with WorkerId.generate]
    F --> G[WorkerRepository.findById]
    G --> H{Worker exists?}
    H -->|Yes| I[Worker.updateHeartbeat]
    H -->|No| J[WorkerRepository.save newWorker]
    I --> K[WorkerRepository.save existingWorker]
    J --> L[SessionToken.generate]
    K --> L
    L --> M[EventPublisher.publishWorkerEvent WorkerDomainEvent.WorkerRegistered]
    M --> N[WorkerRegistrationResponse.success]
    
    style B fill:#e1f5fe
    style C fill:#f3e5f5
    style F fill:#e8f5e8
    style L fill:#fff3e0
```

## 3. Flujo de Comunicación Worker-Server

```mermaid
sequenceDiagram
    participant PipelineWorker
    participant JobExecutorServiceImpl
    participant CreateAndExecuteJobUseCase
    participant PipelineScriptExecutor
    participant ExecutionStrategyManager
    participant KotlinScriptingStrategy
    
    PipelineWorker->>JobExecutorServiceImpl: WorkerToServer.heartbeat
    JobExecutorServiceImpl-->>PipelineWorker: ServerToWorker.ack
    
    JobExecutorServiceImpl->>PipelineWorker: ServerToWorker.jobRequest(ExecuteJobRequest)
    PipelineWorker->>PipelineWorker: ExecuteJobRequest.toDomain()
    PipelineWorker->>PipelineScriptExecutor: execute(Job, WorkerId)
    PipelineScriptExecutor->>ExecutionStrategyManager: getStrategy(JobType.SCRIPT)
    ExecutionStrategyManager-->>PipelineScriptExecutor: KotlinScriptingStrategy
    PipelineScriptExecutor->>KotlinScriptingStrategy: execute(Job, WorkerId, outputHandler)
    
    loop Durante ejecución
        KotlinScriptingStrategy->>PipelineScriptExecutor: JobExecutionEvent.OutputReceived
        PipelineScriptExecutor-->>PipelineWorker: JobExecutionEvent
        PipelineWorker->>PipelineWorker: convertEventToWorkerMessage
        PipelineWorker->>JobExecutorServiceImpl: WorkerToServer.jobOutputAndStatus
    end
    
    KotlinScriptingStrategy->>PipelineScriptExecutor: JobExecutionEvent.Completed
    PipelineScriptExecutor-->>PipelineWorker: JobExecutionEvent.Completed
    PipelineWorker->>JobExecutorServiceImpl: WorkerToServer.jobOutputAndStatus(COMPLETED)
```

## 4. Flujo del Sistema de Cache de Artefactos

```mermaid
flowchart TD
    A[ServerToWorker.cacheQuery] --> B[PipelineWorker.handleCacheQuery]
    B --> C[ArtifactCacheQuery.artifactIdsList]
    C --> D[persistentArtifacts Map lookup]
    D --> E{Artifact cached?}
    E -->|Yes| F[CachedArtifact.checksum]
    E -->|No| G[ArtifactCacheInfo.needsTransfer = true]
    F --> H[ArtifactCacheInfo.cached = true]
    G --> I[ArtifactCacheResponse.build]
    H --> I
    I --> J[WorkerToServer.cacheResponse]
    
    J --> K{Needs Transfer?}
    K -->|Yes| L[ServerToWorker.artifactChunk]
    K -->|No| M[Use cached artifact]
    
    L --> N[PipelineWorker.handleArtifactChunk]
    N --> O[ArtifactDownload.buffer.add]
    O --> P{isLast chunk?}
    P -->|No| Q[ArtifactAck.success]
    P -->|Yes| R[finalizeArtifactWithDecompression]
    R --> S[calculateSha256]
    S --> T[CachedArtifact.create]
    T --> U[persistentArtifacts.put]
    U --> V[ArtifactAck.success with checksum]
    
    style B fill:#e1f5fe
    style D fill:#f3e5f5
    style N fill:#e8f5e8
    style R fill:#fff3e0
    style T fill:#fce4ec
```

## 5. Flujo de Ejecución de DSL Pipeline

```mermaid
flowchart TD
    A[PipelineScript.kts] --> B[PipelineScriptCompilationConfiguration]
    B --> C[ScriptingEngineHost.eval]
    C --> D[PipelineContext.create]
    D --> E[PipelineContext.stage]
    E --> F[StageContext.steps]
    F --> G[StepsContext.sh/script/archiveArtifacts]
    G --> H[PipelineContext.executeShellCommand]
    H --> I[ProcessBuilder.start]
    I --> J[Process.inputStream.bufferedReader]
    J --> K[JobOutputChunk.create]
    K --> L[outputChannel.send]
    L --> M[PipelineEvent.StageCompleted]
    M --> N[eventChannel.send]
    
    E --> O[PipelineContext.parallel]
    O --> P[ParallelContext.stage]
    P --> Q[coroutineScope.async]
    Q --> R[Multiple StageContext.block]
    R --> S[PipelineEvent.ParallelGroupCompleted]
    
    G --> T[PipelineContext.archiveArtifacts]
    T --> U[findFilesByPattern]
    U --> V[File.walkTopDown]
    V --> W[PipelineEvent.ArtifactGenerated]
    
    style D fill:#e1f5fe
    style E fill:#f3e5f5
    style H fill:#e8f5e8
    style O fill:#fff3e0
    style T fill:#fce4ec
```

## 6. Flujo de Estrategias de Ejecución

```mermaid
flowchart TD
    A[Job with JobPayload] --> B[PipelineScriptExecutor.determineJobType]
    B --> C{JobPayload type}
    C -->|Script| D[JobType.SCRIPT]
    C -->|Command| E[JobType.COMMAND]
    C -->|CompiledScript| F[JobType.COMPILED_SCRIPT]
    
    D --> G[ExecutionStrategyManager.getStrategy]
    E --> G
    F --> G
    
    G --> H{Strategy Type}
    H -->|SCRIPT| I[KotlinScriptingStrategy]
    H -->|COMMAND| J[SystemCommandStrategy]
    H -->|COMPILED_SCRIPT| K[CompilerEmbeddableStrategy]
    
    I --> L[ScriptingHost.eval with PipelineContext]
    J --> M[ProcessBuilder with command list]
    K --> N[KotlinCompilerEmbeddable.compile]
    
    L --> O[JobExecutionResult.create]
    M --> O
    N --> O
    
    O --> P[JobExecutionEvent stream]
    P --> Q[PipelineScriptExecutor.convertToEvents]
    
    style B fill:#e1f5fe
    style G fill:#f3e5f5
    style I fill:#e8f5e8
    style J fill:#fff3e0
    style K fill:#fce4ec
    style O fill:#ffebee
```

## 7. Flujo de Gestión de Seguridad

```mermaid
flowchart TD
    A[PipelineContext.script] --> B[PipelineSecurityManager.checkScriptAccess]
    B --> C[SecurityPolicy.rules evaluation]
    C --> D{Script allowed?}
    D -->|No| E[SecurityCheckResult.Denied]
    D -->|Yes| F[SecurityCheckResult.Allowed]
    
    E --> G[SecurityException thrown]
    F --> H[PipelineContext.executeSecureScript]
    
    A2[PipelineContext.library] --> B2[PipelineSecurityManager.checkLibraryAccess]
    B2 --> C2[SecurityPolicy.libraryRules evaluation]
    C2 --> D2{Library allowed?}
    D2 -->|No| E2[SecurityCheckResult.Denied with violations]
    D2 -->|Yes| F2[SecurityCheckResult.Allowed]
    
    E2 --> G2[SecurityException thrown]
    F2 --> H2[LibraryManager.loadLibrary]
    H2 --> I2[LibraryReference.create]
    
    style B fill:#ffcdd2
    style C fill:#ffebee
    style B2 fill:#ffcdd2
    style C2 fill:#ffebee
    style H2 fill:#e8f5e8
```

## 8. Flujo de Testing con Embedded gRPC

```mermaid
flowchart TD
    A[IntegrationTest.setup] --> B[EmbeddedGrpcServer.create]
    B --> C[MockWorkerManagementService.register]
    C --> D[MockJobExecutorService.register]
    D --> E[ServerBuilder.addService]
    E --> F[Server.start on random port]
    F --> G[PipelineWorker.create with server port]
    G --> H[PipelineWorker.start]
    H --> I[WorkerRegistrationRequest sent]
    I --> J[MockWorkerManagementService.registerWorker]
    J --> K[WorkerRegistrationResponse.success]
    K --> L[Test assertions]
    L --> M[PipelineWorker.close]
    M --> N[EmbeddedGrpcServer.stop]
    
    style B fill:#e3f2fd
    style C fill:#f3e5f5
    style G fill:#e8f5e8
    style J fill:#fff3e0
    style L fill:#fce4ec
```

## Clases Principales por Módulo

### Core Domain
- `Job`, `JobId`, `JobDefinition`, `JobStatus`, `JobExecution`
- `Worker`, `WorkerId`, `WorkerCapabilities`, `WorkerStatus`
- `JobScheduler`, `WorkerPool`, `ScalingPolicy`

### Core Application
- `CreateAndExecuteJobUseCase`
- `RegisterWorkerUseCase`
- `JobExecutionResult`, `CreateAndExecuteJobRequest`

### Core Infrastructure
- `InMemoryJobRepository`, `InMemoryWorkerRepository`
- `InMemoryEventPublisher`, `InMemoryJobExecutionService`
- `InMemoryConfiguration`

### Backend Infrastructure
- `JobExecutorServiceImpl`, `WorkerManagementServiceImpl`
- `JobMappers`, `WorkerMappers`
- `SimpleKubernetesOrchestrator`, `WorkerPoolManagerImpl`

### Worker Domain
- `PipelineContext`, `StageContext`, `DSLDefinition`
- `Library`, `LibraryManager`, `SecurityPolicy`
- `JobExecutionStrategy`, `ExecutionStrategyManager`

### Worker Infrastructure
- `PipelineWorker`, `PipelineScriptExecutor`
- `KotlinScriptingStrategy`, `SystemCommandStrategy`, `CompilerEmbeddableStrategy`
- `DefaultExecutionStrategyManager`, `PipelineSecurityManager`

### Worker Application
- `PipelineWorkerApp`, `WorkerConfiguration`
- `DefaultLibraryManager`, `DockerExtension`, `GitExtension`

Estos diagramas muestran exactamente qué clases se utilizan en cada paso del flujo y cómo interactúan entre sí.