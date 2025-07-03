package dev.rubentxu.hodei.security.domain.repositories

import dev.rubentxu.hodei.shared.domain.primitives.DomainId
import dev.rubentxu.hodei.security.domain.entities.AuditLog
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

interface AuditRepository {
    suspend fun save(auditLog: AuditLog): AuditLog
    suspend fun findById(id: DomainId): AuditLog?
    suspend fun findByUserId(userId: DomainId, page: Int = 0, size: Int = 20): List<AuditLog>
    suspend fun findByResource(resource: String, page: Int = 0, size: Int = 20): List<AuditLog>
    suspend fun findByAction(action: String, page: Int = 0, size: Int = 20): List<AuditLog>
    suspend fun findByTimeRange(from: Instant, to: Instant, page: Int = 0, size: Int = 20): List<AuditLog>
    suspend fun findAll(page: Int = 0, size: Int = 20): List<AuditLog>
    fun streamAuditLogs(): Flow<AuditLog>
    suspend fun deleteOlderThan(timestamp: Instant): Int
}