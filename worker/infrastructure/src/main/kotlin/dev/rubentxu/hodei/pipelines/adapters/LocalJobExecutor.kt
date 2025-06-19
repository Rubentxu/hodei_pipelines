package dev.rubentxu.hodei.pipelines.adapters

import dev.rubentxu.hodei.pipelines.port.JobExecutor
import dev.rubentxu.hodei.pipelines.job.JobDefinition
import dev.rubentxu.hodei.pipelines.job.JobExecution
import kotlin.script.experimental.api.ResultValue
import kotlin.script.experimental.api.ScriptDiagnostic
import kotlin.script.experimental.host.toScriptSource
import kotlin.script.experimental.jvmhost.BasicJvmScriptingHost
import kotlin.script.experimental.jvmhost.createJvmCompilationConfigurationFromTemplate

class LocalJobExecutor : JobExecutor {
    override fun execute(job: JobDefinition): JobExecution {
        val compilationConfiguration = createJvmCompilationConfigurationFromTemplate<SimpleScript>()

        val host = BasicJvmScriptingHost()
        val result = host.eval(job.script.toScriptSource(), compilationConfiguration, null)

        val output = result.reports.filter { it.severity == ScriptDiagnostic.Severity.ERROR }
            .joinToString("\n") { it.message }

        return if (result is ResultValue.Failure) {
            JobExecution(job.id, JobExecution.Status.FAILED, output)
        } else {
            JobExecution(job.id, JobExecution.Status.COMPLETED, output)
        }
    }
}

// This is a simple script template. We can extend it as needed.
abstract class SimpleScript
