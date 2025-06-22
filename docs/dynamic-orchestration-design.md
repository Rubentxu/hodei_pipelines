# 🚀 Dynamic Worker Orchestration Design

## 📋 Overview

Sistema de orquestación dinámico para Hodei Pipelines que gestiona workers automáticamente según la demanda, similar al plugin de Kubernetes de Jenkins con agentes JNLP.

## 🏗️ Arquitectura Extendida

```
┌─────────────────────────────────────────────────────────────────┐
│                    Hodei Pipelines Server                       │
├─────────────────────────────────────────────────────────────────┤
│  gRPC API Layer                                                 │
│  ├── WorkerManagementService    ├── JobExecutorService          │
│  └── OrchestrationService       └── WorkerTemplateService       │
├─────────────────────────────────────────────────────────────────┤
│  Application Layer                                              │
│  ├── Job Queue & Scheduler      ├── Worker Pool Manager        │
│  ├── Orchestration Engine       ├── Scaling Policy Engine      │
│  └── Resource Monitor           └── Template Manager            │
├─────────────────────────────────────────────────────────────────┤
│  Domain Layer                                                   │
│  ├── WorkerPool                 ├── JobQueue                    │
│  ├── WorkerTemplate            ├── ScalingPolicy               │
│  ├── ResourceQuota             ├── OrchestrationPlan           │
│  └── WorkerLifecycle           └── SchedulingStrategy          │
├─────────────────────────────────────────────────────────────────┤
│  Infrastructure Layer                                           │
│  ├── Kubernetes Orchestrator    ├── Docker Orchestrator        │
│  ├── In-Memory Repository       ├── Persistent Storage         │
│  └── Metrics & Monitoring       └── Event Streaming            │
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                 Kubernetes Cluster                              │
├─────────────────────────────────────────────────────────────────┤
│  Worker Pods (Dynamic)                                          │
│  ├── hodei-worker-build-xyz    ├── hodei-worker-test-abc        │
│  ├── hodei-worker-deploy-123   ├── hodei-worker-generic-456     │
│  └── [Auto-scaled based on demand]                              │
└─────────────────────────────────────────────────────────────────┘
```

## 🎯 Conceptos Clave

### 1. **WorkerPool** - Pool de Workers Gestionado
```kotlin
data class WorkerPool(
    val id: WorkerPoolId,
    val name: String,
    val template: WorkerTemplate,
    val currentSize: Int,
    val desiredSize: Int,
    val maxSize: Int,
    val scalingPolicy: ScalingPolicy,
    val workers: List<Worker>
)
```

### 2. **WorkerTemplate** - Plantilla de Worker
```kotlin
data class WorkerTemplate(
    val id: WorkerTemplateId,
    val name: String,
    val image: String,
    val resources: ResourceRequirements,
    val capabilities: Map<String, String>,
    val labels: Map<String, String>,
    val environment: Map<String, String>,
    val nodeSelector: Map<String, String>? = null
)
```

### 3. **JobQueue** - Cola de Jobs con Prioridades
```kotlin
data class JobQueue(
    val jobs: PriorityQueue<QueuedJob>,
    val schedulingStrategy: SchedulingStrategy
)

data class QueuedJob(
    val job: Job,
    val priority: JobPriority,
    val queuedAt: Instant,
    val requirements: WorkerRequirements
)
```

### 4. **ScalingPolicy** - Políticas de Auto-escalado
```kotlin
data class ScalingPolicy(
    val minWorkers: Int = 0,
    val maxWorkers: Int = 10,
    val scaleUpThreshold: ScaleThreshold,
    val scaleDownThreshold: ScaleThreshold,
    val cooldownPeriod: Duration = Duration.ofMinutes(2)
)

data class ScaleThreshold(
    val queueLength: Int? = null,
    val avgWaitTime: Duration? = null,
    val workerUtilization: Double? = null
)
```

## 🔄 Flujo de Orquestación

### 1. **Job Submission & Queueing**
```
Job Request → Queue Analysis → Worker Requirements → Schedule/Queue
```

### 2. **Worker Provisioning**
```
Queue Monitor → Scaling Decision → Template Selection → Worker Creation
```

### 3. **Job Execution**
```
Worker Available → Job Assignment → Execution → Result Collection
```

### 4. **Worker Lifecycle**
```
Creation → Registration → Active → Idle → Termination
```

## 🚀 Implementation Plan

### Phase 1: Core Orchestration (High Priority)
1. **Domain Models**: WorkerPool, WorkerTemplate, JobQueue, ScalingPolicy
2. **Orchestration Engine**: Core logic for worker management
3. **Job Queue System**: Priority queue with scheduling
4. **Basic Kubernetes Integration**: Pod creation/deletion

### Phase 2: Advanced Scaling (Medium Priority)
1. **Scaling Policies**: Configurable auto-scaling rules
2. **Resource Monitoring**: CPU, memory, queue metrics
3. **Template Management**: Multiple worker types
4. **Persistent Storage**: State management

### Phase 3: Production Features (Low Priority)
1. **Multi-tenancy**: Namespace isolation
2. **Advanced Scheduling**: Node affinity, taints/tolerations
3. **Monitoring & Observability**: Metrics, tracing
4. **Security**: RBAC, network policies

## 🛠️ Technical Components

### 1. **OrchestrationEngine**
```kotlin
class OrchestrationEngine(
    private val workerPoolManager: WorkerPoolManager,
    private val jobQueue: JobQueue,
    private val kubernetesOrchestrator: KubernetesOrchestrator,
    private val scalingPolicyEngine: ScalingPolicyEngine
) {
    suspend fun processJobSubmission(job: Job)
    suspend fun manageWorkerPools()
    suspend fun handleWorkerEvents(event: WorkerEvent)
}
```

### 2. **KubernetesOrchestrator**
```kotlin
interface WorkerOrchestrator {
    suspend fun createWorker(template: WorkerTemplate): WorkerCreationResult
    suspend fun deleteWorker(workerId: WorkerId): WorkerDeletionResult
    suspend fun listWorkers(poolId: WorkerPoolId): List<Worker>
    suspend fun getWorkerStatus(workerId: WorkerId): WorkerStatus
}
```

### 3. **JobScheduler**
```kotlin
class JobScheduler(
    private val jobQueue: JobQueue,
    private val workerPoolManager: WorkerPoolManager
) {
    suspend fun schedule(job: Job): SchedulingResult
    suspend fun assignJobToWorker(job: Job, worker: Worker): JobAssignment
}
```

## 📊 Scaling Strategies

### 1. **Queue-Based Scaling**
- Scale up when queue length > threshold
- Scale down when workers idle for > cooldown period

### 2. **Predictive Scaling**
- Historical analysis of job patterns
- Pre-emptive worker provisioning

### 3. **Resource-Based Scaling**
- CPU/Memory utilization metrics
- Node resource availability

## 🔧 Configuration Example

```yaml
# hodei-orchestration.yaml
workerPools:
  - name: "build-workers"
    template:
      image: "hodei/worker:build-latest"
      resources:
        cpu: "1000m"
        memory: "2Gi"
      capabilities:
        - "build"
        - "maven"
        - "gradle"
    scaling:
      minWorkers: 1
      maxWorkers: 10
      scaleUpThreshold:
        queueLength: 3
        avgWaitTime: "30s"
      scaleDownThreshold:
        idleTime: "5m"

  - name: "test-workers"
    template:
      image: "hodei/worker:test-latest"
      resources:
        cpu: "500m"
        memory: "1Gi"
      capabilities:
        - "test"
        - "junit"
        - "playwright"
    scaling:
      minWorkers: 0
      maxWorkers: 20
```

## 🎯 Comparison with Jenkins Kubernetes Plugin

| Feature | Jenkins K8s Plugin | Hodei Orchestration |
|---------|-------------------|---------------------|
| Agent Provisioning | Pod Templates | WorkerTemplates |
| Scaling | Manual/Pipeline-based | Automatic/Policy-based |
| Communication | JNLP | gRPC Streaming |
| Resource Management | Basic | Advanced (quotas, monitoring) |
| Multi-tenancy | Limited | Built-in |
| Lifecycle Management | Basic | Full lifecycle hooks |

## 🚨 Next Steps

1. **Implement Domain Models** for orchestration
2. **Create KubernetesOrchestrator** adapter
3. **Build OrchestrationEngine** core logic
4. **Add Job Queue System** with priorities
5. **Integrate with existing gRPC services**
6. **Test end-to-end workflow**