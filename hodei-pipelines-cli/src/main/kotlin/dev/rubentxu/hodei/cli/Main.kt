package dev.rubentxu.hodei.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.*
import dev.rubentxu.hodei.cli.commands.*

/**
 * Hodei Pipelines CLI - Client for Hodei Orchestrator
 * 
 * Similar to kubectl or oc, this CLI is a client that communicates with 
 * a running Hodei orchestrator via REST API.
 */
class HodeiCli : CliktCommand(
    name = "hp",
    help = "🚀 Hodei Pipelines CLI - Distributed Job Orchestration Client"
) {
    
    private val verbose by option(
        "--verbose", "-v",
        help = "Enable verbose output"
    ).flag()
    
    private val config by option(
        "--config", "-c",
        help = "Path to CLI configuration file"
    ).default("~/.hodei/config")
    
    private val context by option(
        "--context",
        help = "Use a specific context from config"
    )
    
    override fun run() {
        if (verbose) {
            println("🚀 Hodei Pipelines CLI")
            println("📁 Config: $config")
            context?.let { println("🎯 Context: $it") }
            println()
        }
    }
}

/**
 * Authentication commands
 */
class AuthCommand : CliktCommand(
    name = "login",
    help = "🔐 Authenticate with Hodei orchestrator"
) {
    override fun run() = Unit
}

/**
 * Pool management commands  
 */
class PoolCommand : CliktCommand(
    name = "pool",
    help = "🏊 Resource pool management"
) {
    override fun run() = Unit
}

/**
 * Job management commands
 */
class JobCommand : CliktCommand(
    name = "job", 
    help = "📋 Job management"
) {
    override fun run() = Unit
}

/**
 * Worker management commands
 */
class WorkerCommand : CliktCommand(
    name = "worker",
    help = "👷 Worker management"
) {
    override fun run() = Unit
}

/**
 * Template management commands
 */
class TemplateCommand : CliktCommand(
    name = "template",
    help = "📦 Template management"
) {
    override fun run() = Unit
}

/**
 * Docker integration commands
 */
class DockerCommand : CliktCommand(
    name = "docker",
    help = "🐳 Docker integration"
) {
    override fun run() = Unit
}

/**
 * Main entry point for the CLI
 */
fun main(args: Array<String>) {
    HodeiCli()
        .subcommands(
            // Authentication
            LoginCommand(),
            LogoutCommand(),
            WhoAmICommand(),
            
            // Resource management
            PoolCommand().subcommands(
                PoolListCommand(),
                PoolCreateCommand(),
                PoolDeleteCommand(),
                PoolStatusCommand(),
                PoolDescribeCommand()
            ),
            
            // Job management
            JobCommand().subcommands(
                JobListCommand(),
                JobSubmitCommand(),
                JobStatusCommand(),
                JobLogsCommand(),
                JobCancelCommand(),
                JobDescribeCommand(),
                JobExecCommand(),
                JobShellCommand()
            ),
            
            // Worker management
            WorkerCommand().subcommands(
                WorkerListCommand(),
                WorkerStatusCommand(),
                WorkerDescribeCommand(),
                WorkerExecCommand(),
                WorkerShellCommand()
            ),
            
            // Template management
            TemplateCommand().subcommands(
                TemplateListCommand(),
                TemplateCreateCommand(),
                TemplateShowCommand(),
                TemplateDescribeCommand()
            ),
            
            // Docker integration
            DockerCommand().subcommands(
                DockerDiscoverCommand(),
                DockerStatusCommand()
            ),
            
            // System commands
            VersionCommand(),
            HealthCommand(),
            StatusCommand(),
            ConfigCommand().subcommands(
                ConfigGetContextsCommand(),
                ConfigUseContextCommand(),
                ConfigCurrentContextCommand()
            )
        )
        .main(args)
}