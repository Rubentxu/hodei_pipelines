package dev.rubentxu.hodei.pipelines.application.worker.dsl.extensions

import dev.rubentxu.hodei.pipelines.domain.worker.model.dsl.ExtensionContext
import dev.rubentxu.hodei.pipelines.domain.worker.ports.PipelineExtension

/**
 * Base class for pipeline extensions
 */
abstract class ExtensionBase : PipelineExtension {
    protected lateinit var context: ExtensionContext

    override fun initialize(context: ExtensionContext) {
        this.context = context
        onInitialize()
    }

    protected open fun onInitialize() {
        // Override in subclasses
    }

    override fun getGlobalVariables(): Map<String, Any> {
        return emptyMap()
    }

    override fun cleanup() {
        // Override in subclasses if needed
    }
}