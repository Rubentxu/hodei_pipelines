package dev.rubentxu.hodei.pipelines.steps.registration

import dev.rubentxu.hodei.pipelines.dsl.builders.StepsBuilder
import dev.rubentxu.hodei.pipelines.dsl.execution.StepExecutor
import dev.rubentxu.hodei.pipelines.dsl.execution.steps.StepCategory
import dev.rubentxu.hodei.pipelines.dsl.extensions.*
import dev.rubentxu.hodei.pipelines.dsl.model.Step
import dev.rubentxu.hodei.pipelines.steps.basic.WorkflowBasicStepsExtension
import dev.rubentxu.hodei.pipelines.steps.utility.PipelineUtilityStepsExtension

/**
 * Extensión principal que registra toda la librería de steps.
 * Combina workflow-basic-steps y pipeline-utility-steps.
 */
class StepsLibraryExtension : BaseStepExtension() {
    override val name: String = "hodei-steps-library"
    override val version: String = "1.0.0"
    override val category: StepCategory = StepCategory.CUSTOM
    override val description: String = "Complete Jenkins-compatible steps library for Hodei Pipelines"
    
    override val dependencies: List<Dependency> = listOf(
        // Jackson for JSON/XML/YAML
        Dependency("com.fasterxml.jackson.core", "jackson-core", "2.15.3"),
        Dependency("com.fasterxml.jackson.module", "jackson-module-kotlin", "2.15.3"),
        Dependency("com.fasterxml.jackson.dataformat", "jackson-dataformat-yaml", "2.15.3"),
        Dependency("com.fasterxml.jackson.dataformat", "jackson-dataformat-xml", "2.15.3"),
        Dependency("com.fasterxml.jackson.dataformat", "jackson-dataformat-csv", "2.15.3"),
        
        // File operations
        Dependency("org.apache.commons", "commons-compress", "1.24.0"),
        Dependency("org.apache.commons", "commons-lang3", "3.13.0"),
        Dependency("commons-codec", "commons-codec", "1.16.0"),
        
        // JSON/XML processing
        Dependency("com.jayway.jsonpath", "json-path", "2.8.0"),
        Dependency("net.sf.saxon", "Saxon-HE", "12.3"),
        Dependency("org.jsoup", "jsoup", "1.16.2"),
        
        // HTTP client
        Dependency("org.apache.httpcomponents.client5", "httpclient5", "5.2.1")
    )
    
    private val workflowBasicSteps = WorkflowBasicStepsExtension()
    private val pipelineUtilitySteps = PipelineUtilityStepsExtension()
    
    override fun createExecutor(): StepExecutor = StepsLibraryExecutor()
    
    override fun registerDslFunctions(builder: StepsBuilder) {
        // Las funciones DSL se registran automáticamente como extension functions
        // cuando se importa el paquete dev.rubentxu.hodei.pipelines.steps.dsl
    }
    
    override fun validate(step: Step): List<String> {
        return when {
            step is ExtensionStep && step.extensionName == "workflow-basic-steps" ->
                workflowBasicSteps.validate(step)
            step is ExtensionStep && step.extensionName == "pipeline-utility-steps" ->
                pipelineUtilitySteps.validate(step)
            else -> super.validate(step)
        }
    }
}

/**
 * Ejecutor compuesto que delega a los ejecutores específicos.
 */
class StepsLibraryExecutor : StepExecutor {
    private val workflowExecutor = WorkflowBasicStepsExtension().createExecutor()
    private val utilityExecutor = PipelineUtilityStepsExtension().createExecutor()
    
    override suspend fun execute(step: Step, context: dev.rubentxu.hodei.pipelines.dsl.execution.PipelineContext) {
        require(step is ExtensionStep) { "Expected ExtensionStep" }
        
        when (step.extensionName) {
            "workflow-basic-steps" -> workflowExecutor.execute(step, context)
            "pipeline-utility-steps" -> utilityExecutor.execute(step, context)
            else -> throw UnsupportedOperationException("Unknown extension: ${step.extensionName}")
        }
    }
}