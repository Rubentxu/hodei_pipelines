package dev.rubentxu.hodei.pipelines.domain.job

/**
 * Represents the actual work to be done by a job.
 * Using a sealed interface allows us to explicitly define the different types of payloads,
 * making the domain model clearer and more type-safe.
 */
sealed interface JobPayload {
    /**
     * A payload that contains a Kotlin script to be executed.
     * @property content The raw Kotlin script content.
     */
    data class Script(val content: String) : JobPayload

    /**
     * A payload that contains a shell command to be executed as a system process.
     * @property commandLine The list of command-line arguments.
     */
    data class Command(val commandLine: List<String>) : JobPayload
}
