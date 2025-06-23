package dev.rubentxu.hodei.pipelines.dsl.script

import kotlin.script.experimental.annotations.KotlinScript
import kotlin.script.experimental.api.*
import kotlin.script.experimental.jvm.dependenciesFromCurrentContext
import kotlin.script.experimental.jvm.jvm

/**
 * Configuración de compilación para scripts .pipeline.kts
 * 
 * Integra con el sistema de workers existente proporcionando
 * acceso completo al DSL y las dependencias necesarias.
 */
object PipelineScriptCompilationConfiguration : ScriptCompilationConfiguration({
    
    // Configuración base del script
    baseClass(PipelineScript::class)
    
    // Imports implícitos para el DSL
    defaultImports(
        // DSL principal
        "dev.rubentxu.hodei.pipelines.dsl.pipeline",
        "dev.rubentxu.hodei.pipelines.dsl.model.*",
        
        // Integración con workers
        "dev.rubentxu.hodei.pipelines.domain.worker.model.dsl.*",
        "dev.rubentxu.hodei.pipelines.port.*",
        
        // Tipos comunes
        "dev.rubentxu.hodei.pipelines.domain.job.*",
        "dev.rubentxu.hodei.pipelines.domain.worker.*",
        
        // Kotlin estándar
        "kotlinx.coroutines.*",
        "kotlinx.serialization.*"
    )
    
    // Dependencias del classpath
    jvm {
        dependenciesFromCurrentContext(wholeClasspath = true)
    }
    
    // Configuración IDE
    ide {
        acceptedLocations(ScriptAcceptedLocation.Everywhere)
    }
    
    // Permitir archivos .pipeline.kts
    fileExtension("pipeline.kts")
})

/**
 * Template base para scripts .pipeline.kts
 * 
 * Proporciona el contexto base y las importaciones necesarias
 * para definir pipelines usando el DSL integrado.
 */
@KotlinScript(
    fileExtension = "pipeline.kts",
    compilationConfiguration = PipelineScriptCompilationConfiguration::class
)
abstract class PipelineScript {
    
    companion object {
        /**
         * Metadatos del script template.
         */
        const val VERSION = "1.0.0"
        const val DESCRIPTION = "Pipeline DSL Script Template"
    }
}

/**
 * Configuración de evaluación para scripts .pipeline.kts
 */
object PipelineScriptEvaluationConfiguration : ScriptEvaluationConfiguration({
    
    // Configuración de evaluación específica si es necesaria
    jvm {
        // Configuraciones JVM específicas para la evaluación
    }
    
    // Constructor implícito
    constructorArgs()
    
    // Scripts de inicialización si son necesarios
    scriptsInstancesSharing(false)
})