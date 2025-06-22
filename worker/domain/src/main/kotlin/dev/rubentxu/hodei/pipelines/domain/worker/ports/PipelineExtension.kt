package dev.rubentxu.hodei.pipelines.domain.worker.ports

import dev.rubentxu.hodei.pipelines.domain.worker.model.dsl.ExtensionContext
import dev.rubentxu.hodei.pipelines.domain.worker.model.dsl.StepDefinition

/**
 * Pipeline extension interface
 */
interface PipelineExtension {
    val identifier: String
    val version: String
    val description: String
    val author: String
    val minimumPipelineVersion: String?

    fun initialize(context: ExtensionContext)
    fun getSteps(): Map<String, StepDefinition>
    fun getGlobalVariables(): Map<String, Any>
    fun cleanup()
}