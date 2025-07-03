package dev.rubentxu.hodei

import io.ktor.server.application.*
import io.ktor.server.netty.*
import dev.rubentxu.hodei.infrastructure.config.configureModules
import dev.rubentxu.hodei.worker.main as workerMain
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

fun main(args: Array<String>) {
    // Parse mode from arguments
    val mode = parseMode(args)
    logger.info { "Starting Hodei Pipelines in mode: $mode" }
    
    when (mode) {
        ApplicationMode.ORCHESTRATOR -> {
            logger.info { "Starting Orchestrator (Ktor Server)..." }
            EngineMain.main(filterOrchestratorArgs(args))
        }
        ApplicationMode.WORKER -> {
            logger.info { "Starting Worker..." }
            workerMain(filterWorkerArgs(args))
        }
    }
}

private fun parseMode(args: Array<String>): ApplicationMode {
    val modeArg = args.find { it.startsWith("--mode=") }
    
    return when {
        modeArg?.substringAfter("=") == "worker" -> ApplicationMode.WORKER
        modeArg?.substringAfter("=") == "orchestrator" -> ApplicationMode.ORCHESTRATOR
        args.contains("worker") -> ApplicationMode.WORKER
        args.contains("--worker-id") -> ApplicationMode.WORKER
        else -> ApplicationMode.ORCHESTRATOR // Default mode
    }
}

private fun filterOrchestratorArgs(args: Array<String>): Array<String> {
    // Remove worker-specific arguments
    return args.filterNot { arg ->
        arg.startsWith("--mode=") || 
        arg == "worker" ||
        arg.startsWith("--worker-id") ||
        arg.startsWith("--orchestrator-host") ||
        arg.startsWith("--orchestrator-port") ||
        arg.startsWith("--work-dir")
    }.toTypedArray()
}

private fun filterWorkerArgs(args: Array<String>): Array<String> {
    // Remove orchestrator-specific arguments and mode
    return args.filterNot { arg ->
        arg.startsWith("--mode=") ||
        arg == "orchestrator"
    }.toTypedArray()
}

enum class ApplicationMode {
    ORCHESTRATOR,
    WORKER
}

fun Application.module() {
    configureModules()
}