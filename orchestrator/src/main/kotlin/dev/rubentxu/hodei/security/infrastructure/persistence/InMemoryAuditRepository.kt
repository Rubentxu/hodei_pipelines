package dev.rubentxu.hodei.security.infrastructure.persistence

import dev.rubentxu.hodei.shared.domain.primitives.DomainId
import dev.rubentxu.hodei.security.domain.repositories.AuditRepository
import dev.rubentxu.hodei.security.domain.entities.AuditLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.Instant
import java.util.concurrent.ConcurrentHashMap

class InMemoryAuditRepository : AuditRepository {
    private val auditLogs = ConcurrentHashMap<DomainId, AuditLog>()
    
    override suspend fun save(auditLog: AuditLog): AuditLog {
        auditLogs[auditLog.id] = auditLog
        return auditLog
    }
    
    override suspend fun findById(id: DomainId): AuditLog? {
        return auditLogs[id]
    }
    
    override suspend fun findByUserId(userId: DomainId, page: Int, size: Int): List<AuditLog> {
        val logs = auditLogs.values.filter { it.userId == userId }
            .sortedByDescending { it.timestamp }
        
        return paginateResults(logs, page, size)
    }
    
    override suspend fun findByResource(resource: String, page: Int, size: Int): List<AuditLog> {
        val logs = auditLogs.values.filter { it.resource == resource }
            .sortedByDescending { it.timestamp }
        
        return paginateResults(logs, page, size)
    }
    
    override suspend fun findByAction(action: String, page: Int, size: Int): List<AuditLog> {
        val logs = auditLogs.values.filter { it.action == action }
            .sortedByDescending { it.timestamp }
        
        return paginateResults(logs, page, size)
    }
    
    override suspend fun findByTimeRange(from: Instant, to: Instant, page: Int, size: Int): List<AuditLog> {
        val logs = auditLogs.values.filter { it.timestamp >= from && it.timestamp <= to }
            .sortedByDescending { it.timestamp }
        
        return paginateResults(logs, page, size)
    }
    
    override suspend fun findAll(page: Int, size: Int): List<AuditLog> {
        val logs = auditLogs.values.sortedByDescending { it.timestamp }
        return paginateResults(logs, page, size)
    }
    
    override fun streamAuditLogs(): Flow<AuditLog> {
        return flowOf(*auditLogs.values.toTypedArray())
    }
    
    override suspend fun deleteOlderThan(timestamp: Instant): Int {
        val toDelete = auditLogs.values.filter { it.timestamp < timestamp }
        toDelete.forEach { auditLogs.remove(it.id) }
        return toDelete.size
    }
    
    private fun paginateResults(logs: List<AuditLog>, page: Int, size: Int): List<AuditLog> {
        val startIndex = page * size
        val endIndex = minOf(startIndex + size, logs.size)
        
        return if (startIndex < logs.size) {
            logs.subList(startIndex, endIndex)
        } else {
            emptyList()
        }
    }
}