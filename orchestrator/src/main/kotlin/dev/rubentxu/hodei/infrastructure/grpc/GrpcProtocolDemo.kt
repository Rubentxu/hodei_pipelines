package dev.rubentxu.hodei.infrastructure.grpc

import kotlinx.coroutines.*
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Demonstration class showing how the simplified gRPC protocol works
 * alongside the existing complex protocol
 */
class GrpcProtocolDemo {
    
    fun demonstrateProtocolComparison() {
        logger.info { "=== gRPC Protocol Evolution Demonstration ===" }
        
        println("""
        |
        |🔄 ANTES: Protocolo Complejo (múltiples RPCs)
        |─────────────────────────────────────────────
        |• RegisterWorker(WorkerRegistration) → stream WorkerInstruction
        |• SendHeartbeat(HeartbeatRequest) → HeartbeatResponse  
        |• ReportExecutionStatus(ExecutionStatusReport) → Empty
        |• SendExecutionEvent(ExecutionEventReport) → Empty
        |• StreamLogs(stream LogEntry) → Empty
        |• RequestResource(ResourceRequest) → ResourceResponse
        |• ReleaseResource(ResourceRelease) → Empty
        |
        |❌ Problemas:
        |   - 7 métodos RPC diferentes
        |   - Manejo complejo de múltiples streams
        |   - Sincronización difícil entre diferentes canales
        |   - Código repetitivo para manejo de errores
        |   - Estado distribuido entre múltiples conexiones
        |
        |✅ AHORA: Protocolo Simplificado (bidireccional)
        |───────────────────────────────────────────
        |• Connect(stream WorkerMessage) → stream OrchestratorMessage
        |
        |💡 Beneficios:
        |   - UN SOLO método RPC bidireccional
        |   - Un canal unificado para toda la comunicación
        |   - Manejo de estado centralizado
        |   - Menos complejidad de código
        |   - Mejor rendimiento (menos overhead)
        |   - Más fácil de debuggear y monitorear
        |
        """.trimMargin())
    }
    
    suspend fun demonstrateSimpleWorker() {
        logger.info { "=== Demonstrating Simple Worker Protocol ===" }
        
        println("""
        |
        |🚀 Worker Simple en Acción:
        |──────────────────────────
        |1. Conecta con un único stream bidireccional
        |2. Envía registro inicial
        |3. Mantiene heartbeat automático
        |4. Recibe y ejecuta tareas
        |5. Envía logs en tiempo real
        |6. Reporta resultados
        |
        |📝 Ejemplo de flujo de mensajes:
        |
        """.trimMargin())
        
        // Simulate worker interaction
        val workerId = "demo-worker-${System.currentTimeMillis()}"
        
        println("🔗 Worker → Orchestrator:")
        println("   RegisterRequest { worker_id: '$workerId', pool_id: 'default' }")
        
        println("\n🔗 Orchestrator → Worker:")
        println("   ExecutionAssignment { execution_id: 'job-123', task: ShellTask }")
        
        println("\n🔗 Worker → Orchestrator:")
        println("   StatusUpdate { execution_id: 'job-123', status: RUNNING }")
        
        println("\n🔗 Worker → Orchestrator:")
        println("   LogChunk { execution_id: 'job-123', stream: STDOUT, content: 'Starting...' }")
        
        println("\n🔗 Worker → Orchestrator:")
        println("   ExecutionResult { execution_id: 'job-123', success: true, exit_code: 0 }")
        
        println("\n✅ Ejecución completada exitosamente!")
    }
    
    fun demonstrateBackwardCompatibility() {
        println("""
        |
        |🔄 Compatibilidad hacia Atrás:
        |─────────────────────────────
        |
        |El WorkerServiceAdapter actúa como un puente:
        |
        |  Nuevo Worker              Adapter              Viejo Orchestrator
        |      │                       │                        │
        |      │ WorkerMessage         │                        │
        |      ├──────────────────────>│                        │
        |      │                       │ WorkerRegistration     │
        |      │                       ├───────────────────────>│
        |      │                       │                        │
        |      │                       │ stream WorkerInstruction│
        |      │                       │<───────────────────────┤
        |      │ OrchestratorMessage   │                        │
        |      │<──────────────────────┤                        │
        |
        |💡 Esto permite:
        |   ✓ Workers nuevos con protocolo simple
        |   ✓ Orchestrator existente sin cambios
        |   ✓ Migración gradual
        |   ✓ Testing de ambos protocolos
        |
        """.trimMargin())
    }
    
    fun demonstrateRealTimeStreaming() {
        println("""
        |
        |📡 Streaming en Tiempo Real:
        |──────────────────────────
        |
        |Con el protocolo simplificado, obtenemos:
        |
        |🔄 Logs en tiempo real:
        |   Worker envía LogChunk inmediatamente cuando hay output
        |   
        |📊 Status updates instantáneos:
        |   StatusUpdate se envía cuando cambia el estado
        |   
        |💗 Heartbeat automático:
        |   Cada 30 segundos para mantener la conexión viva
        |   
        |⚡ Cancelación inmediata:
        |   CancelSignal se propaga instantáneamente
        |
        |🎯 Casos de uso ideales:
        |   • CI/CD pipelines con feedback inmediato
        |   • Monitoring en tiempo real de jobs
        |   • Debug interactivo de ejecuciones
        |   • Streaming de logs para dashboards
        |
        """.trimMargin())
    }
    
    fun demonstrateTaskTypes() {
        println("""
        |
        |🛠️ Tipos de Tareas Soportadas:
        |─────────────────────────────
        |
        |1️⃣ ShellTask - Comandos simples:
        |   ExecutionDefinition {
        |     shell: ShellTask {
        |       commands: ["echo 'Hello'", "ls -la", "date"]
        |       allow_failure: false
        |     }
        |   }
        |
        |2️⃣ KotlinScriptTask - Scripts Kotlin:
        |   ExecutionDefinition {
        |     kotlin_script: KotlinScriptTask {
        |       script_content: "println(parameters['message'])"
        |       parameters: { "message": "Hello from Kotlin!" }
        |     }
        |   }
        |
        |3️⃣ PipelineTask - Pipelines complejos:
        |   ExecutionDefinition {
        |     pipeline: PipelineTask {
        |       stages: [
        |         { name: "build", steps: [{ command: "gradle build" }] },
        |         { name: "test", steps: [{ command: "gradle test" }] }
        |       ]
        |     }
        |   }
        |
        |✨ El worker determina automáticamente cómo ejecutar cada tipo
        |
        """.trimMargin())
    }
}

// Extension function to run the demo
suspend fun runGrpcDemo() {
    val demo = GrpcProtocolDemo()
    
    demo.demonstrateProtocolComparison()
    delay(2000)
    
    demo.demonstrateSimpleWorker()
    delay(2000)
    
    demo.demonstrateBackwardCompatibility()
    delay(2000)
    
    demo.demonstrateRealTimeStreaming()
    delay(2000)
    
    demo.demonstrateTaskTypes()
    
    println("\n🎉 Demo completado! El nuevo protocolo está listo para uso.")
}