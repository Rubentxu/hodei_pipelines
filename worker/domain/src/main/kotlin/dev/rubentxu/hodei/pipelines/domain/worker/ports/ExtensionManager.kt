package dev.rubentxu.hodei.pipelines.domain.worker.ports

import dev.rubentxu.hodei.pipelines.domain.worker.model.dsl.StepDefinition
import java.io.File

/**
 * Extension management system for pipeline DSL
 */
interface ExtensionManager {
    fun loadExtension(extensionPath: File): PipelineExtension
    fun registerExtension(extension: PipelineExtension)
    fun getExtension(identifier: String): PipelineExtension?
    fun getAllExtensions(): Map<String, PipelineExtension>
    fun getAvailableSteps(): Map<String, StepDefinition>
    fun getGlobalVariables(): Map<String, Any>
    fun unloadExtension(identifier: String)
}