# Worker Integration with Pipeline DSL

## Visión de la Integración

Los workers ahora usarán **exclusivamente** el Pipeline DSL para ejecutar todos los tipos de jobs, reemplazando completamente el sistema de múltiples estrategias.

## Arquitectura Antes vs Después

### ❌ ANTES (Complejo)
```
Worker
├── ExecutionStrategyManager
├── KotlinScriptingStrategy  
├── SystemCommandStrategy
├── CompilerEmbeddableStrategy
├── DefaultExtensionManager
├── DefaultLibraryManager
└── PipelineScriptExecutor
```

### ✅ DESPUÉS (Simplificado)
```
Worker
└── PipelineDslStrategy
    └── PipelineOrchestrator
        ├── PipelineEngine
        ├── PipelineRunner
        └── StepExecutorManager
```

## Integración en Worker

### 1. Reemplazar PipelineScriptExecutor

**Antes:**
```kotlin
// worker/infrastructure/PipelineScriptExecutor.kt
class PipelineScriptExecutor(
    private val strategyManager: ExecutionStrategyManager = createDefaultStrategyManager()
) : ScriptExecutor {
    // Múltiples estrategias complejas...
}
```

**Después:**
```kotlin
// worker/infrastructure/PipelineScriptExecutor.kt  
class PipelineScriptExecutor : ScriptExecutor {
    
    private val pipelineDslStrategy = PipelineDslStrategy()
    
    override fun execute(job: Job, workerId: WorkerId): Flow<JobExecutionEvent> = channelFlow {
        send(JobExecutionEvent.Started(job.id, workerId))
        
        try {
            // TODO tipo de job se convierte a Pipeline DSL automáticamente
            val result = pipelineDslStrategy.execute(job, workerId) { chunk ->
                runBlocking { send(JobExecutionEvent.OutputReceived(job.id, chunk)) }
            }
            
            when (result.status) {
                JobStatus.COMPLETED -> send(JobExecutionEvent.Completed(job.id, result.exitCode, result.output))
                JobStatus.FAILED -> send(JobExecutionEvent.Failed(job.id, result.errorMessage ?: "Unknown error", result.exitCode))
                else -> send(JobExecutionEvent.Failed(job.id, "Unexpected status: ${result.status}", result.exitCode))
            }
            
        } catch (e: Exception) {
            send(JobExecutionEvent.Failed(job.id, "Pipeline DSL execution failed: ${e.message}", 1))
        }
    }
}
```

### 2. Actualizar PipelineWorker

**Antes:**
```kotlin
// worker/infrastructure/PipelineWorker.kt
class PipelineWorker {
    private val scriptExecutor = PipelineScriptExecutor.createDefaultStrategyManager()
    // Múltiples sistemas...
}
```

**Después:**  
```kotlin
// worker/infrastructure/PipelineWorker.kt
class PipelineWorker {
    private val scriptExecutor = PipelineScriptExecutor() // Simplificado!
    // Un solo sistema Pipeline DSL
}
```

## Beneficios de la Migración

### 🎯 **Simplificación Radical**
- **De 6+ clases a 1**: Una sola estrategia Pipeline DSL
- **De múltiples configuraciones a una**: Un solo orquestador
- **De múltiples paths de ejecución a uno**: Todo vía Pipeline DSL

### 🔄 **Compatibilidad Total**
- **Scripts Kotlin** → Se envuelven en Pipeline DSL automáticamente
- **Comandos sistema** → Se convierten a steps Pipeline DSL
- **Scripts .pipeline.kts** → Se compilan directamente
- **Jobs compilados** → Ya son Pipeline DSL

### 📈 **Mejoras Operacionales**
- **Logging unificado**: Todo vía Pipeline DSL
- **Métricas consistentes**: Misma estructura para todos los jobs
- **Error handling mejorado**: Stack traces limpios y contextuales
- **Testing simplificado**: Una sola ruta de código

## Ejemplo de Conversión Automática

### Script Kotlin Original
```kotlin
// Job con payload Script
println("Hello from Kotlin!")
val result = "gradle build".execute()
println("Build result: $result")
```

### Se Convierte Automáticamente a:
```kotlin
pipeline("Script Execution") {
    stages {
        stage("Execute Script") {
            steps {
                echo("Hello from Kotlin!")
                sh("gradle build")
                echo("Build completed")
            }
        }
    }
}
```

### Comando Sistema Original
```bash
# Job con payload Command
cd /app && gradle clean build
```

### Se Convierte Automáticamente a:
```kotlin
pipeline("Command Execution") {
    stages {
        stage("Execute Command") {
            steps {
                dir("/app") {
                    sh("gradle clean build")
                }
            }
        }
    }
}
```

## Plan de Migración

### Fase 1: Implementar PipelineDslStrategy ✅
- [x] Crear WorkerIntegration.kt
- [x] Implementar conversores automáticos
- [x] Strategy que maneja todos los tipos de job

### Fase 2: Reemplazar en Worker Infrastructure
- [ ] Actualizar PipelineScriptExecutor
- [ ] Simplificar PipelineWorker  
- [ ] Remover estrategias antiguas

### Fase 3: Testing y Validación
- [ ] Tests de conversión automática
- [ ] Tests de integración worker
- [ ] Verificar compatibilidad con jobs existentes

### Fase 4: Cleanup
- [ ] Remover clases obsoletas
- [ ] Actualizar documentación
- [ ] Actualizar dependencias

## Resultado Final

Un sistema worker **drasticamente simplificado** donde:

1. **Un solo punto de entrada**: PipelineDslStrategy
2. **Conversión automática**: Cualquier job → Pipeline DSL  
3. **Ejecución unificada**: Todo vía PipelineEngine
4. **Mantenimiento mínimo**: Una sola arquitectura

¡Los workers se vuelven **mucho más simples y potentes** al mismo tiempo! 🚀