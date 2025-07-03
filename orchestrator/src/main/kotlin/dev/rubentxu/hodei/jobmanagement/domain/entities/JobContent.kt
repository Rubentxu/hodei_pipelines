package dev.rubentxu.hodei.jobmanagement.domain.entities

import kotlinx.serialization.Serializable

@Serializable
sealed class JobContent {
    @Serializable
    data class KotlinScript(
        val scriptContent: String,
        val timeout: String? = null
    ) : JobContent()
    
    @Serializable
    data class ShellCommands(
        val commands: List<String>,
        val timeout: String? = null
    ) : JobContent()
}