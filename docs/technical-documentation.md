# Documentación Técnica: Hodei Pipelines

## Resumen Ejecutivo

**Hodei Pipelines** es un sistema de orquestación de pipelines CI/CD distribuido desarrollado en Kotlin, implementando una arquitectura hexagonal con comunicación gRPC entre componentes. El sistema permite la ejecución distribuida de trabajos (jobs) a través de workers remotos con capacidades avanzadas de cache, compresión y DSL dinámico.

## Arquitectura General

```mermaid
graph TB
    subgraph "Server Tier"
        Server[Hodei Pipelines Server]
        Server --> WMS[Worker Management Service]
        Server --> JES[Job Executor Service]
    end
    
    subgraph "Core Domain"
        Job[(Job Domain)]
        Worker[(Worker Domain)]
        Orchestration[(Orchestration)]
    end
    
    subgraph "Worker Tier"
        W1[Pipeline Worker 1]
        W2[Pipeline Worker 2]
        WN[Pipeline Worker N]
    end
    
    subgraph "Storage"
        JobRepo[(Job Repository)]
        WorkerRepo[(Worker Repository)]
        ArtifactCache[(Artifact Cache)]
    end
    
    Server -.->|gRPC| W1
    Server -.->|gRPC| W2
    Server -.->|gRPC| WN
    
    Server --> JobRepo
    Server --> WorkerRepo
    W1 --> ArtifactCache
    W2 --> ArtifactCache
```

## Módulos del Sistema

### 1. Módulo Worker

**Ubicación**: `/worker/`

**Responsabilidades**:
- Ejecutor autónomo de pipelines
- Cliente gRPC que se conecta al servidor central
- Gestión de cache local de artefactos
- Ejecución de DSL dinámico de pipelines

#### Componentes Principales:

**PipelineWorker** (`worker/infrastructure/src/main/kotlin/.../PipelineWorker.kt`):
- Cliente gRPC principal
- Gestión de heartbeat y comunicación bidireccional
- Cache de artefactos con compresión GZIP
- Registro/desregistro automático de workers

```mermaid
sequenceDiagram
    participant Server
    participant Worker
    participant ScriptExecutor
    
    Worker->>Server: Register Worker
    Server-->>Worker: Registration ACK
    
    loop Heartbeat
        Worker->>Server: Heartbeat
        Server-->>Worker: ACK
    end
    
    Server->>Worker: Job Request
    Worker->>ScriptExecutor: Execute Job
    ScriptExecutor-->>Worker: Execution Events
    Worker->>Server: Job Status Updates
    Worker->>Server: Job Output Chunks
    Worker->>Server: Job Completion
```

**PipelineScriptExecutor** (`worker/infrastructure/src/main/kotlin/.../PipelineScriptExecutor.kt`):
- Patrón Strategy para múltiples tipos de ejecución
- Soporte para scripts Kotlin, comandos del sistema y scripts compilados
- Manejo de eventos en tiempo real

**Estrategias de Ejecución**:
- `KotlinScriptingStrategy`: Ejecución de scripts Kotlin dinámicos
- `SystemCommandStrategy`: Ejecución de comandos del sistema
- `CompilerEmbeddableStrategy`: Scripts pre-compilados

#### DSL de Pipeline Context

El sistema proporciona un contexto rico para la ejecución de pipelines con capacidades avanzadas:

**PipelineContext** (`worker/domain/src/main/kotlin/.../dsl/PipelineContext.kt`):
- Gestión de stages con eventos
- Ejecución paralela de stages
- Archivo de artefactos con validación
- Gestión de seguridad para scripts y librerías
- Contextos especializados (Environment, Tools, SCM, Docker)

### 2. Módulo Domain (Core)

**Ubicación**: `/core/domain/`

**Responsabilidades**:
- Entidades de negocio principales
- Reglas de dominio y validaciones
- Interfaces de puertos (ports)

#### Entidades Principales:

**Job** (`core/domain/src/main/kotlin/.../job/Job.kt`):
```kotlin
data class Job(
    val id: JobId,
    val definition: JobDefinition,
    val status: JobStatus = JobStatus.QUEUED,
    val execution: JobExecution? = null,
    val assignedWorker: WorkerId? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now()
)
```

**Estados de Job**:
- `QUEUED` → `RUNNING` → `COMPLETED`/`FAILED`/`CANCELLED`

**Worker** (`core/domain/src/main/kotlin/.../worker/Worker.kt`):
```kotlin
data class Worker(
    val id: WorkerId,
    val name: String,
    val capabilities: WorkerCapabilities,
    val status: WorkerStatus = WorkerStatus.IDLE,
    val activeJobs: Int = 0,
    val lastHeartbeat: Instant = Instant.now()
)
```

```mermaid
stateDiagram-v2
    [*] --> QUEUED: Create Job
    QUEUED --> RUNNING: Assign Worker
    QUEUED --> CANCELLED: Cancel Request
    RUNNING --> COMPLETED: Success
    RUNNING --> FAILED: Error
    RUNNING --> CANCELLED: Cancel Request
    COMPLETED --> [*]
    FAILED --> [*]
    CANCELLED --> [*]
```

#### Capacidades de Workers

El sistema de capacidades permite matching inteligente de jobs con workers:

```kotlin
data class WorkerCapabilities(
    private val capabilities: Map<String, String>
) {
    fun matches(key: String, value: String): Boolean
    fun hasLabel(label: String): Boolean
    fun getOperatingSystem(): String?
    fun getArchitecture(): String?
}
```

### 3. Módulo Application

**Ubicación**: `/core/application/`

**Responsabilidades**:
- Casos de uso del sistema
- Orquestación de flujos de negocio
- Coordinación entre capas

#### Casos de Uso Principales:

**CreateAndExecuteJobUseCase** (`core/application/src/main/kotlin/.../CreateAndExecuteJobUseCase.kt`):
- Flujo completo de creación y ejecución de jobs
- Selección automática de workers
- Monitoreo de ejecución en tiempo real

```mermaid
flowchart TD
    A[Create Job Request] --> B[Validate Job Definition]
    B --> C[Save Job to Repository]
    C --> D[Find Available Workers]
    D --> E{Workers Available?}
    E -->|No| F[Fail Job - No Workers]
    E -->|Yes| G[Select Best Worker]
    G --> H[Assign Worker to Job]
    H --> I[Start Job Execution]
    I --> J[Monitor Execution]
    J --> K{Execution Result}
    K -->|Success| L[Complete Job]
    K -->|Failure| M[Fail Job]
    K -->|Output| N[Stream Output]
    N --> J
```

**RegisterWorkerUseCase** (`core/application/src/main/kotlin/.../RegisterWorkerUseCase.kt`):
- Registro de nuevos workers
- Validación de capacidades
- Generación de tokens de sesión
- Manejo de workers duplicados

### 4. Módulo Infrastructure

**Ubicación**: `/core/infrastructure/` y `/backend/infrastructure/`

**Responsabilidades**:
- Implementaciones técnicas de los puertos
- Servicios gRPC
- Repositorios en memoria
- Gestión de eventos

#### Servicios gRPC:

**WorkerManagementServiceImpl** (`backend/infrastructure/src/main/kotlin/.../WorkerManagementServiceImpl.kt`):
- Registro y gestión de workers
- Manejo de heartbeats
- Control de lifecycle de workers

**JobExecutorServiceImpl** (`backend/infrastructure/src/main/kotlin/.../JobExecutorServiceImpl.kt`):
- Canal bidireccional de comunicación
- Transferencia de artefactos con cache
- Streaming de output en tiempo real

#### Repositorios:

**InMemoryJobRepository** (`core/infrastructure/src/main/kotlin/.../InMemoryJobRepository.kt`):
- Almacenamiento en memoria de jobs
- Operaciones CRUD thread-safe
- Búsquedas por estado y worker

**InMemoryWorkerRepository** (`core/infrastructure/src/main/kotlin/.../InMemoryWorkerRepository.kt`):
- Gestión de workers registrados
- Filtrado por capacidades y disponibilidad
- Cleanup automático de workers offline

### 5. Módulo App

**Ubicación**: `/backend/application/` y `/worker/app/`

**Responsabilidades**:
- Puntos de entrada de aplicaciones
- Configuración e inicialización
- Manejo de comandos CLI

#### HodeiPipelinesServer (`backend/application/src/main/kotlin/.../HodeiPipelinesServer.kt`):
- Servidor gRPC principal
- Configuración automática de dependencias
- Manejo graceful de shutdown
- Reflection service para discovery

#### PipelineWorkerApp (`worker/app/src/main/kotlin/.../PipelineWorkerApp.kt`):
- Cliente worker independiente
- Configuración por variables de entorno y CLI
- Auto-detección de capacidades del sistema
- Banner y logging configurables

## DSL de Pipelines

El sistema incluye un DSL robusto para definir pipelines:

```kotlin
pipeline {
    agent {
        label("linux && x64")
    }
    
    environment {
        JAVA_HOME = "/usr/lib/jvm/java-11"
        PATH = "${env.PATH}:${env.JAVA_HOME}/bin"
    }
    
    stages {
        stage("Build") {
            steps {
                sh("./gradlew build")
                archiveArtifacts("build/libs/*.jar")
            }
        }
        
        stage("Test") {
            parallel {
                stage("Unit Tests") {
                    steps {
                        sh("./gradlew test")
                        publishTestResults("build/test-results/test/*.xml")
                    }
                }
                stage("Integration Tests") {
                    steps {
                        sh("./gradlew integrationTest")
                        publishTestResults("build/test-results/integrationTest/*.xml")
                    }
                }
            }
        }
        
        stage("Deploy") {
            when_(env.BRANCH_NAME == "main") {
                steps {
                    script {
                        def version = readFile("VERSION").trim()
                        sh("./deploy.sh ${version}")
                    }
                }
            }
        }
    }
    
    post {
        always {
            archiveArtifacts("logs/*.log")
        }
        failure {
            emailext(
                subject: "Build Failed: ${env.JOB_NAME}",
                body: "Build failed. Check the logs for details."
            )
        }
    }
}
```

### Extensiones Disponibles

El sistema DSL incluye extensiones predefinidas:

**DockerExtension**: Operaciones Docker integradas
**GitExtension**: Gestión de repositorios Git
**NotificationExtension**: Sistema de notificaciones
**DefaultExtensionManager**: Gestión central de extensiones

## Sistema de Cache de Artefactos

### Características Avanzadas:

1. **Cache Inteligente**: 
   - Checksums SHA-256 para verificación de integridad
   - Cache persistente entre ejecuciones
   - Queries de cache antes de transferencias

2. **Compresión**:
   - GZIP automático para reducir transferencias
   - Soporte futuro para ZSTD
   - Descompresión transparente

3. **Transferencia por Chunks**:
   - Streaming de artefactos grandes
   - Acknowledgments por chunk
   - Recuperación de transferencias interrumpidas

```mermaid
sequenceDiagram
    participant Server
    participant Worker
    participant Cache
    
    Server->>Worker: Cache Query (artifact IDs)
    Worker->>Cache: Check Local Cache
    Cache-->>Worker: Cache Status
    Worker-->>Server: Cache Response
    
    alt Artifact Not Cached
        Server->>Worker: Artifact Chunk 1
        Worker->>Cache: Store Chunk
        Worker-->>Server: ACK Chunk 1
        Server->>Worker: Artifact Chunk N (last)
        Worker->>Cache: Finalize Artifact
        Worker-->>Server: ACK Final
    else Artifact Cached
        Worker-->>Server: Cache Hit
    end
```

## Testing

### Cobertura de Tests:

- **Tests unitarios**: 67+ tests ejecutados exitosamente
- **Tests de integración**: Incluye `BasicIntegrationTest` con 100% éxito
- **Mock services**: Servicios gRPC mockeados para testing aislado
- **Estrategia**: Embedded gRPC servers para simplicidad

### Tests Destacados:

**Domain Tests**:
- `JobTest`: Estados y transiciones de jobs ✅
- `WorkerTest`: Gestión de capacidades y estados ✅
- `JobSchedulerTest`: Algoritmos de scheduling ✅

**Integration Tests**:
- `WorkerRegistrationIntegrationTest`: Integración completa server-worker ✅
- `BasicIntegrationTest`: Flujo end-to-end básico ✅
- `MinimalIntegrationTest`: Casos mínimos de funcionalidad ✅

**Infrastructure Tests**:
- `PipelineScriptExecutorTest`: Ejecución de scripts con diferentes estrategias ✅
- `PipelineSecurityManagerTest`: Validaciones de seguridad ✅
- `ExtensionManagerTest`: Gestión de extensiones DSL ✅

### Embedded Testing Infrastructure

**EmbeddedGrpcServer** (`worker/infrastructure/src/test/kotlin/.../EmbeddedGrpcServer.kt`):
- Servidor gRPC embebido para tests
- Mock services configurables
- Lifecycle automático de test

## Características Avanzadas

### 1. Seguridad en Pipeline Scripts

**PipelineSecurityManager** (`worker/infrastructure/src/main/kotlin/.../PipelineSecurityManager.kt`):
- Validación de scripts antes de ejecución
- Control de acceso a librerías externas
- Políticas de seguridad configurables
- Sandbox de ejecución

### 2. Gestión de Librerías Dinámicas

**DefaultLibraryManager** (`worker/application/src/main/kotlin/.../DefaultLibraryManager.kt`):
- Carga dinámica de librerías externas
- Resolución de dependencias
- Cache de librerías descargadas
- Validación de checksums

### 3. Sistema de Eventos

El sistema emite eventos detallados para monitoreo:

```kotlin
sealed class PipelineEvent {
    data class StageStarted(val jobId: JobId, val stageName: String, val type: StageType)
    data class StageCompleted(val jobId: JobId, val stageName: String, val duration: Long, val status: StageStatus)
    data class StageFailed(val jobId: JobId, val stageName: String, val error: String, val cause: Throwable?, val duration: Long)
    data class ParallelGroupStarted(val jobId: JobId, val groupName: String, val stageNames: List<String>)
    data class ParallelGroupCompleted(val jobId: JobId, val groupName: String, val duration: Long, val successful: List<String>, val failed: List<String>)
    data class ProgressUpdate(val jobId: JobId, val current: Int, val total: Int, val message: String)
    data class ArtifactGenerated(val jobId: JobId, val artifactName: String, val artifactPath: String, val artifactSize: Long, val artifactType: String)
    data class CustomEvent(val jobId: JobId, val eventType: String, val eventName: String, val data: Map<String, Any>)
}
```

### 4. Estrategias de Ejecución

El sistema usa el patrón Strategy para soportar múltiples tipos de ejecución:

**ExecutionStrategyManager** (`worker/domain/src/main/kotlin/.../ExecutionStrategyManager.kt`):
- Registro dinámico de estrategias
- Selección automática por tipo de job
- Extensibilidad para nuevos tipos

## Flujo de Ejecución Completo

```mermaid
sequenceDiagram
    participant Client
    participant Server
    participant Worker1
    participant Worker2
    participant Cache
    
    Client->>Server: Create Job Request
    Server->>Server: Validate & Save Job
    
    par Worker Registration
        Worker1->>Server: Register with capabilities
        Worker2->>Server: Register with capabilities
    end
    
    Server->>Server: Find suitable worker
    Server->>Worker1: Assign Job
    Worker1->>Server: Accept Job
    
    Server->>Worker1: Cache Query (artifacts)
    Worker1->>Cache: Check Cache
    Cache-->>Worker1: Cache Status
    Worker1-->>Server: Cache Response
    
    opt Artifacts Needed
        Server->>Worker1: Transfer Artifacts
        Worker1->>Cache: Store Artifacts
    end
    
    Worker1->>Worker1: Execute Pipeline Script
    loop During Execution
        Worker1->>Server: Stream Output
        Worker1->>Server: Progress Updates
        Worker1->>Server: Stage Events
    end
    
    Worker1->>Server: Job Completed
    Server->>Client: Execution Result
```

## Configuración y Deployment

### Variables de Entorno del Servidor:
```bash
SERVER_PORT=9090                    # Puerto del servidor gRPC
LOG_LEVEL=INFO                      # Nivel de logging
```

### Variables de Entorno del Worker:
```bash
WORKER_ID=worker-001                # ID único del worker
WORKER_NAME="Build Worker 1"        # Nombre descriptivo
SERVER_HOST=localhost               # Host del servidor
SERVER_PORT=9090                    # Puerto del servidor
MAX_CONCURRENT_JOBS=5               # Jobs concurrentes máximos
WORKER_LABELS=linux,x64,docker      # Labels para matching
WORKER_CAPABILITIES=java=11,docker=20.10  # Capacidades adicionales
```

### Comandos CLI:

**Servidor**:
```bash
# Iniciar servidor en puerto por defecto
java -jar hodei-server.jar

# Iniciar en puerto específico
java -jar hodei-server.jar 8080
```

**Worker**:
```bash
# Worker con configuración por defecto
java -jar hodei-worker.jar

# Worker con configuración específica
java -jar hodei-worker.jar \
  --worker-id build-worker-1 \
  --worker-name "Build Worker 1" \
  --server-host prod-server \
  --server-port 9090
```

## Monitoreo y Observabilidad

### Logging Estructurado:
- Uso de `mu.KotlinLogging` para logging consistente
- Niveles: TRACE, DEBUG, INFO, WARN, ERROR
- Contexto de job y worker en todos los logs
- Timestamps precisos para correlación

### Métricas de Sistema:
- Tiempo de ejecución de jobs
- Throughput de workers
- Cache hit ratio de artefactos
- Latencia de comunicación gRPC

### Health Checks:
- Heartbeat automático de workers (10 segundos)
- Detección de workers offline
- Estado de conexiones gRPC
- Validación de capacidades del sistema

## Roadmap y Extensiones Futuras

### Características Planificadas:

1. **Persistence Layer**:
   - Soporte para bases de datos relacionales
   - Migrations automáticas
   - Backup y restore de estado

2. **Web UI**:
   - Dashboard de monitoreo
   - Gestión visual de pipelines
   - Logs en tiempo real

3. **Clustering**:
   - Múltiples instancias de servidor
   - Load balancing automático
   - Failover de jobs

4. **Security Enhancements**:
   - Autenticación y autorización
   - TLS para comunicación gRPC
   - Audit logging

5. **Advanced Scheduling**:
   - Priority queues
   - Resource-based scheduling
   - Job dependencies

## Conclusiones

Hodei Pipelines implementa un sistema robusto de orquestación con:

✅ **Arquitectura Hexagonal**: Separación clara de responsabilidades  
✅ **Comunicación gRPC**: Alta performance y bidireccional  
✅ **DSL Expresivo**: Pipeline definitions legibles y potentes  
✅ **Cache Inteligente**: Optimización de transferencias de artefactos  
✅ **Testing Comprehensivo**: Cobertura amplia con tests de integración  
✅ **Escalabilidad**: Soporte para múltiples workers distribuidos  
✅ **Seguridad**: Validación de scripts y gestión de permisos  
✅ **Observabilidad**: Logging estructurado y eventos detallados  

El sistema está preparado para entornos de producción con características empresariales como manejo de errores, logging estructurado, configuración flexible, y extensibilidad para futuras características.

### Métricas del Proyecto:
- **Módulos**: 5 módulos principales
- **Líneas de código**: ~15,000+ líneas
- **Tests**: 67+ tests unitarios e integración
- **Cobertura**: Alto coverage en componentes críticos
- **Arquitectura**: Hexagonal con DDD
- **Tecnologías**: Kotlin, gRPC, Coroutines, JUnit 5