package dev.rubentxu.hodei.pipelines.domain.worker.ports

import dev.rubentxu.hodei.pipelines.domain.worker.model.dsl.FileOperation
import dev.rubentxu.hodei.pipelines.domain.worker.model.security.SecurityCheckResult
import dev.rubentxu.hodei.pipelines.domain.worker.model.security.SecurityPolicy

/**
 * Security manager for pipeline execution
 */
interface PipelineSecurityManager {
    fun checkScriptAccess(script: String): SecurityCheckResult
    fun checkLibraryAccess(libraryId: String): SecurityCheckResult
    fun checkFileAccess(path: String, operation: FileOperation): SecurityCheckResult
    fun checkNetworkAccess(host: String, port: Int): SecurityCheckResult
    fun checkSystemPropertyAccess(property: String): SecurityCheckResult
    val securityPolicy: SecurityPolicy
}