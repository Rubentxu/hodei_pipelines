package dev.rubentxu.hodei.pipelines.infrastructure.script

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
        "dev.rubentxu.hodei.pipelines.infrastructure.script.PipelineContext",
        "dev.rubentxu.hodei.pipelines.infrastructure.script.TaskContainer",
        "dev.rubentxu.hodei.pipelines.infrastructure.script.PipelineTask"
    )

    // Configure JVM-specific options
    jvm {
        // Use the classpath of the current context (the application running the script)
        dependenciesFromCurrentContext(wholeClasspath = true)
    }
})