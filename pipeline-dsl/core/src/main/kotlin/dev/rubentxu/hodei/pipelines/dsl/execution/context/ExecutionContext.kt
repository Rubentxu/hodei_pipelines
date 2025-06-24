package dev.rubentxu.hodei.pipelines.dsl.execution.context

import dev.rubentxu.hodei.pipelines.dsl.model.PipelineExecutionEvent
import dev.rubentxu.hodei.pipelines.dsl.model.PipelineOutputChunk
import kotlinx.coroutines.channels.SendChannel
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Contexto base de ejecución siguiendo el principio de responsabilidad única.
 * Solo gestiona el estado de ejecución y variables.
 */
@DslMarker
annotation class ExecutionContextMarker

@ExecutionContextMarker
open class ExecutionContext(
    val jobId: String,
    val workerId: String,
    val workingDirectory: File = File(System.getProperty("user.dir")),
    val environment: MutableMap<String, String> = mutableMapOf()
) {
    // Variables del pipeline con acceso thread-safe
    private val variables: MutableMap<String, Any> = ConcurrentHashMap()
    
    /**
     * Establece una variable del pipeline.
     */
    fun setVariable(key: String, value: Any) {
        variables[key] = value
    }
    
    /**
     * Obtiene una variable del pipeline de forma tipada.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> getVariable(key: String): T? = variables[key] as? T
    
    /**
     * Obtiene una variable con valor por defecto.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> getVariable(key: String, default: T): T = 
        variables[key] as? T ?: default
    
    /**
     * Verifica si existe una variable.
     */
    fun hasVariable(key: String): Boolean = variables.containsKey(key)
    
    /**
     * Obtiene todas las variables.
     */
    fun getAllVariables(): Map<String, Any> = variables.toMap()
    
    /**
     * Establece una variable de entorno.
     */
    fun setEnv(key: String, value: String) {
        environment[key] = value
    }
    
    /**
     * Obtiene una variable de entorno.
     */
    fun getEnv(key: String): String? = environment[key]
    
    /**
     * Obtiene una variable de entorno con valor por defecto.
     */
    fun getEnv(key: String, default: String): String = 
        environment[key] ?: default
}