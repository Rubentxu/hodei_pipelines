package dev.rubentxu.hodei.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.*
import com.github.ajalt.clikt.parameters.types.int
import dev.rubentxu.hodei.cli.commands.*
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Hodei Pipelines CLI - Unified command-line interface for orchestrator management
 * 
 * This CLI provides commands for:
 * - Docker worker discovery and management
 * - Orchestrator server operations
 * - Pipeline execution and monitoring
 * - Resource pool management
 */
class HodeiCli : CliktCommand(
    name = "hodei",
    help = "🚀 Hodei Pipelines - Distributed Job Orchestration Platform"
) {
    
    private val verbose by option(
        "--verbose", "-v",
        help = "Enable verbose output"
    ).flag()
    
    private val configPath by option(
        "--config", "-c",
        help = "Path to configuration file"
    ).default("application.conf")
    
    override fun run() {
        if (verbose) {
            logger.info { "Starting Hodei Pipelines CLI" }
            logger.info { "Config path: $configPath" }
        }
    }
}

/**
 * Docker management command group
 */
class DockerCommand : CliktCommand(
    name = "docker",
    help = "🐳 Docker worker management commands"
) {
    override fun run() = Unit
}

/**
 * Server management command group
 */
class ServerCommand : CliktCommand(
    name = "server",
    help = "🖥️ Orchestrator server management commands"
) {
    override fun run() = Unit
}

/**
 * Resource pool management command group
 */
class PoolCommand : CliktCommand(
    name = "pool",
    help = "🏊 Resource pool management commands"
) {
    override fun run() = Unit
}

/**
 * Worker template management command group
 */
class TemplateCommand : CliktCommand(
    name = "template",
    help = "📦 Worker template management commands"
) {
    override fun run() = Unit
}

/**
 * Main entry point for the CLI
 */
fun main(args: Array<String>) {
    HodeiCli()
        .subcommands(
            DockerCommand().subcommands(
                DockerDiscoverCommand(),
                DockerStartCommand(),
                DockerStopCommand(),
                DockerStatusCommand(),
                DockerWorkerCommand()
            ),
            ServerCommand().subcommands(
                ServerStartCommand(),
                ServerStopCommand(),
                ServerStatusCommand()
            ),
            PoolCommand().subcommands(
                PoolListCommand(),
                PoolCreateCommand(),
                PoolDeleteCommand(),
                PoolStatusCommand()
            ),
            TemplateCommand().subcommands(
                TemplateListCommand(),
                TemplateCreateCommand(),
                TemplateShowCommand()
            ),
            // Standalone commands
            WorkerCommand(),
            OrchestratorCommand(),
            HealthCommand(),
            VersionCommand()
        )
        .main(args)
}