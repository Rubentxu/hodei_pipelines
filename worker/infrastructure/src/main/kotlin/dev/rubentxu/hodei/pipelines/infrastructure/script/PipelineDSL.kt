package dev.rubentxu.hodei.pipelines.infrastructure.script

import kotlin.script.experimental.annotations.KotlinScript


/**
 * Kotlin Script template for Pipeline DSL
 * Similar to Gradle Kotlin DSL
 */
@KotlinScript(
    fileExtension = "pipeline.kts",
    compilationConfiguration = PipelineScriptCompilationConfiguration::class
)
abstract class PipelineScript
