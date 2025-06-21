# Patrones de Sistema: hodei-pipelines

## 1. Arquitectura Principal: Arquitectura Hexagonal

El proyecto `hodei-pipelines` está diseñado siguiendo los principios de la **Arquitectura Hexagonal (o Arquitectura de Puertos y Adaptadores)**. Esta decisión arquitectónica es fundamental para lograr un bajo acoplamiento, alta cohesión y una excelente testabilidad del sistema.

La estructura de módulos de Gradle refleja esta separación:

- **Dominio (`core:domain`)**: Es el núcleo del sistema. Contiene las entidades, eventos de dominio y la lógica de negocio más pura. No conoce detalles de infraestructura.
- **Aplicación (`core:application`, `backend:application`, `worker:application`)**: Contiene los casos de uso que orquestan la lógica de dominio. Define los *puertos* (interfaces) que necesita para interactuar con el mundo exterior (p. ej., `JobRepository`, `EventPublisher`).
- **Infraestructura (`core:infrastructure`, `backend:infrastructure`, `worker:infrastructure`)**: Implementa los *adaptadores* que conectan los puertos de la capa de aplicación con tecnologías concretas. Por ejemplo, un adaptador gRPC para la API, o un adaptador de base de datos para la persistencia.

```mermaid
graph TD
    subgraph "Capa de Dominio (core:domain)"
        direction LR
        J(Job) -- Contiene --> JD(JobDefinition)
        JD -- Especifica --> JP(JobPayload)
        subgraph JobPayload
            direction TB
            Script
            Command
        end

        JS(JobScheduler) -- Gestiona --> JQ(JobQueue)
        JS -- Asigna Job a --> W(Worker)
        JQ -- Almacena --> J

        WP(WorkerPool) -- Gestiona --> W
        WP -- Usa --> SP(ScalingPolicy)
        SP -- Usa --> WT(WorkerTemplate)
        W -- Tiene --> WC(WorkerCapabilities)
    end

    subgraph "Capa de Aplicación"
        CU[Casos de Uso]
    end

    subgraph "Capa de Infraestructura"
        A[Adaptador gRPC]
        DB[Adaptador de Persistencia]
    end

    A -- Invoca --> CU
    CU -- Orquesta --> J
    CU -- Orquesta --> JS
    CU -- Orquesta --> WP
    DB -- Implementa Puerto de --> J

    classDef domain fill:#f9f,stroke:#333,stroke-width:2px;
    class J,JD,JP,Script,Command,JS,JQ,WP,W,SP,WT,WC domain;
```

## 2. Principios de Diseño

- **SOLID**: Los principios SOLID son una guía fundamental para el diseño de clases y componentes dentro de cada capa, buscando crear un código mantenible y extensible.
- **Clean Code**: Se busca activamente escribir un código limpio, legible y expresivo.

## 3. Patrones de Diseño Clave

- **Inyección de Dependencias (DI)**: Se utiliza para desacoplar los componentes, especialmente para inyectar las implementaciones de los adaptadores en los casos de uso.
- **Repositorio**: Para abstraer el acceso a los datos de las entidades del dominio.
- **Caso de Uso (Servicio de Aplicación)**: Para encapsular y orquestar la lógica de negocio de la aplicación.

## 4. Flujo de Ejecución de un Trabajo (Diagrama de Secuencia)

El siguiente diagrama de secuencia ilustra el flujo completo de un trabajo, desde su creación hasta su ejecución en un worker.

```mermaid
sequenceDiagram
    participant Client
    participant Backend (gRPC Server)
    participant AppUseCase as CreateAndExecuteJobUseCase
    participant JobRepo as JobRepository
    participant Orchestrator as OrchestrationCoordinator
    participant WorkerRepo as WorkerRepository
    participant Worker (gRPC Client)
    participant ScriptExecutor as PipelineScriptExecutor

    Client->>+Backend: createJob(jobDefinition)
    Backend->>+AppUseCase: execute(jobDefinition)
    AppUseCase->>AppUseCase: Crear entidad Job (status: QUEUED)
    AppUseCase->>+JobRepo: save(job)
    JobRepo-->>-AppUseCase: jobId
    AppUseCase-->>-Backend: jobResponse (con jobId)
    Backend-->>Client: jobResponse

    loop Ciclo de Orquestación
        Orchestrator->>Orchestrator: scheduleJobs()
        Orchestrator->>+JobRepo: findByStatus(QUEUED)
        JobRepo-->>-Orchestrator: [Job]
        Orchestrator->>+WorkerRepo: findAvailableWorkers()
        WorkerRepo-->>-Orchestrator: [Worker]
        Orchestrator->>Orchestrator: Asignar Job a Worker
    end

    Orchestrator->>+Worker: execute(job)
    Note right of Worker: Canal gRPC bidireccional

    Worker->>+ScriptExecutor: execute(job.payload)
    ScriptExecutor->>ScriptExecutor: Ejecutar script/comando
    
    loop Flujo de Logs y Estado
        ScriptExecutor-->>Worker: "Log line 1"
        Worker-->>Backend: send(logEvent)
        ScriptExecutor-->>Worker: "Log line 2"
        Worker-->>Backend: send(logEvent)
    end

    ScriptExecutor-->>-Worker: ExecutionResult (SUCCESS/FAILURE)
    Worker->>Backend: send(jobCompletedEvent)
    Backend->>+JobRepo: save(job) (actualizar estado)
    JobRepo-->>-Backend: OK
    Worker-->>-Orchestrator: OK
```
