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

    fun sh(command: String): String {
        try {
            val process = ProcessBuilder("/bin/sh", "-c", command)
                .redirectErrorStream(true)
                .start()

            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()

            if (exitCode != 0) {
                throw RuntimeException("Command '$command' failed with exit code $exitCode and output:\n$output")
            }
            println(output)
            return output
        } catch (e: Exception) {
            throw RuntimeException("Failed to execute command '$command'", e)
        }
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
    private val doFirstActions = mutableListOf<() -> Unit>()
    private val doLastActions = mutableListOf<() -> Unit>()

    fun dependsOn(vararg taskNames: String) {
        dependencies.addAll(taskNames)
    }

    fun doFirst(action: () -> Unit) {
        doFirstActions.add(action)
    }

    fun doLast(action: () -> Unit) {
        doLastActions.add(action)
    }

    fun execute() {
        val executedTasks = mutableSetOf<String>()
        executeWithDependencies(executedTasks)
    }

    private fun executeWithDependencies(executedTasks: MutableSet<String>) {
        if (executedTasks.contains(name)) {
            return
        }

        dependencies.forEach { depName ->
            container.findByName(depName)?.executeWithDependencies(executedTasks)
        }

        container.getContext().println("Executing task: $name")
        doFirstActions.forEach { it() }
        doLastActions.forEach { it() }
        executedTasks.add(name)
    }
}