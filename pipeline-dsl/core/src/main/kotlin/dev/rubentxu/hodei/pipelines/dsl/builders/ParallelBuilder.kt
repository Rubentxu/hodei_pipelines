package dev.rubentxu.hodei.pipelines.dsl.builders

import dev.rubentxu.hodei.pipelines.dsl.PipelineDslMarker
import dev.rubentxu.hodei.pipelines.dsl.model.Step

/**
 * Builder para ejecución paralela compatible con Jenkins Pipeline DSL.
 */
@PipelineDslMarker
class ParallelBuilder(private val failFast: Boolean = true) {
    private val branches = mutableMapOf<String, List<Step>>()
    
    /**
     * Define una rama paralela con un nombre y steps.
     */
    fun branch(name: String, block: StepsBuilder.() -> Unit) {
        val builder = StepsBuilder()
        builder.block()
        branches[name] = builder.build()
    }
    
    /**
     * Operador invoke para sintaxis más fluida.
     */
    operator fun String.invoke(block: StepsBuilder.() -> Unit) {
        branch(this, block)
    }
    
    internal fun build(): Step.Parallel {
        return Step.Parallel(
            branches = branches,
            failFast = failFast
        )
    }
}