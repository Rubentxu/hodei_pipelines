package dev.rubentxu.hodei.pipelines.infrastructure.script

import dev.rubentxu.hodei.pipelines.domain.worker.model.dsl.PipelineContext
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.defaultImports
import kotlin.script.experimental.api.implicitReceivers
import kotlin.script.experimental.jvm.dependenciesFromCurrentContext
import kotlin.script.experimental.jvm.jvm

object PipelineScriptCompilationConfiguration : ScriptCompilationConfiguration({
    // Define implicit receivers. `this` in the script will be a PipelineContext
    implicitReceivers(PipelineContext::class)

    // Define default imports available in the script
    defaultImports(
        "dev.rubentxu.hodei.pipelines.domain.worker.model.dsl.PipelineContext",
    )

    // Configure JVM-specific options
    jvm {
        // Use the classpath of the current context (the application running the script)
        dependenciesFromCurrentContext(wholeClasspath = true)
    }
})