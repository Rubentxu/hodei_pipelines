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

/**
 * Pipeline Context - provides the DSL context similar to Gradle's Project
 */
class PipelineContext(
    private val environment: Map<String, String>,
    private val outputCapture: StringBuilder
) {
    val tasks = TaskContainer(this)
    val env = mutableMapOf<String, String>().apply { putAll(environment) }
    
    fun println(message: Any?) {
        val text = message.toString()
        outputCapture.appendLine(text)
        kotlin.io.println(text) // Also print to actual stdout for debugging
    }
}

/**
 * Task Container - manages tasks like Gradle's TaskContainer
 */
class TaskContainer(private val context: PipelineContext) {
    private val tasks = mutableMapOf<String, PipelineTask>()
    
    fun register(name: String, configuration: PipelineTask.() -> Unit = {}): PipelineTask {
        val task = PipelineTask(name, this)
        task.configuration()
        tasks[name] = task
        return task
    }
    
    fun getByName(name: String): PipelineTask {
        return tasks[name] ?: throw IllegalArgumentException("Task '$name' not found")
    }
    
    fun findByName(name: String): PipelineTask? {
        return tasks[name]
    }
    
    internal fun getAllTasks(): Map<String, PipelineTask> = tasks.toMap()
    internal fun getContext(): PipelineContext = context
}

/**
 * Pipeline Task - similar to Gradle's Task
 */
class PipelineTask(val name: String, private val container: TaskContainer) {
    private val dependencies = mutableSetOf<String>()
    private val doFirstActions = mutableListOf<PipelineContext.() -> Unit>()
    private val doLastActions = mutableListOf<PipelineContext.() -> Unit>()
    private var executed = false
    
    fun dependsOn(vararg taskNames: String) {
        dependencies.addAll(taskNames)
    }
    
    fun doFirst(action: PipelineContext.() -> Unit) {
        doFirstActions.add(action)
    }
    
    fun doLast(action: PipelineContext.() -> Unit) {
        doLastActions.add(action)
    }
    
    fun execute() {
        execute(container.getContext())
    }
    
    internal fun execute(context: PipelineContext) {
        if (executed) return
        
        // Execute dependencies first
        dependencies.forEach { depName ->
            val depTask = context.tasks.getByName(depName)
            depTask.execute(context)
        }
        
        // Execute this task
        try {
            doFirstActions.forEach { action -> context.action() }
            doLastActions.forEach { action -> context.action() }
            executed = true
        } catch (e: Exception) {
            throw RuntimeException("Task '$name' failed: ${e.message}", e)
        }
    }
    
    internal fun getDependencies(): Set<String> = dependencies.toSet()
    internal fun isExecuted(): Boolean = executed
    internal fun reset() { executed = false }
}

/**
 * Script compilation configuration
 */
object PipelineScriptCompilationConfiguration : kotlin.script.experimental.api.ScriptCompilationConfiguration({
    // For now, keep it simple to get the MVP working
})