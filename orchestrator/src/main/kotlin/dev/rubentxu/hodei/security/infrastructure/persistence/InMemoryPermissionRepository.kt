package dev.rubentxu.hodei.security.infrastructure.persistence

import dev.rubentxu.hodei.shared.domain.primitives.DomainId
import dev.rubentxu.hodei.security.domain.repositories.PermissionRepository
import dev.rubentxu.hodei.security.domain.entities.Permission
import kotlinx.datetime.Clock
import java.util.concurrent.ConcurrentHashMap

class InMemoryPermissionRepository : PermissionRepository {
    private val permissions = ConcurrentHashMap<DomainId, Permission>()
    private val resourceActionIndex = ConcurrentHashMap<String, DomainId>()
    
    init {
        // Initialize with default permissions
        initializeDefaultPermissions()
    }
    
    override suspend fun findById(id: DomainId): Permission? {
        return permissions[id]
    }
    
    override suspend fun findByResourceAndAction(resource: String, action: String): Permission? {
        val key = "$resource:$action"
        val permissionId = resourceActionIndex[key] ?: return null
        return permissions[permissionId]
    }
    
    override suspend fun save(permission: Permission): Permission {
        permissions[permission.id] = permission
        resourceActionIndex[permission.fullName] = permission.id
        return permission
    }
    
    override suspend fun update(permission: Permission): Permission {
        val existingPermission = permissions[permission.id] 
            ?: throw IllegalArgumentException("Permission not found: ${permission.id}")
        
        // Update index if resource or action changed
        if (existingPermission.fullName != permission.fullName) {
            resourceActionIndex.remove(existingPermission.fullName)
            resourceActionIndex[permission.fullName] = permission.id
        }
        
        permissions[permission.id] = permission
        return permission
    }
    
    override suspend fun delete(id: DomainId): Boolean {
        val permission = permissions.remove(id) ?: return false
        resourceActionIndex.remove(permission.fullName)
        return true
    }
    
    override suspend fun findAll(): List<Permission> {
        return permissions.values.toList()
    }
    
    override suspend fun findByResource(resource: String): List<Permission> {
        return permissions.values.filter { it.resource == resource }
    }
    
    override suspend fun findSystemPermissions(): List<Permission> {
        return permissions.values.filter { it.isSystem }
    }
    
    private fun initializeDefaultPermissions() {
        val now = Clock.System.now()
        
        val permissions = listOf(
            // Job permissions
            createPermission("job", "create", "Create new jobs", true, now),
            createPermission("job", "read", "Read job information", true, now),
            createPermission("job", "update", "Update job configuration", true, now),
            createPermission("job", "delete", "Delete jobs", true, now),
            createPermission("job", "execute", "Execute jobs", true, now),
            
            // Template permissions
            createPermission("template", "create", "Create job templates", true, now),
            createPermission("template", "read", "Read template information", true, now),
            createPermission("template", "update", "Update templates", true, now),
            createPermission("template", "delete", "Delete templates", true, now),
            
            // Pool permissions
            createPermission("pool", "create", "Create resource pools", true, now),
            createPermission("pool", "read", "Read pool information", true, now),
            createPermission("pool", "update", "Update pool configuration", true, now),
            createPermission("pool", "delete", "Delete resource pools", true, now),
            
            // Worker permissions
            createPermission("worker", "register", "Register new workers", true, now),
            createPermission("worker", "read", "Read worker information", true, now),
            createPermission("worker", "update", "Update worker status", true, now),
            createPermission("worker", "terminate", "Terminate workers", true, now),
            
            // Execution permissions
            createPermission("execution", "monitor", "Monitor job executions", true, now),
            createPermission("execution", "cancel", "Cancel running executions", true, now),
            
            // Admin permissions
            createPermission("system", "admin", "Full system administration", true, now),
            createPermission("audit", "read", "Read audit logs", true, now),
            createPermission("user", "manage", "Manage user accounts", true, now),
            createPermission("role", "manage", "Manage roles and permissions", true, now)
        )
        
        permissions.forEach { permission ->
            this.permissions[permission.id] = permission
            resourceActionIndex[permission.fullName] = permission.id
        }
    }
    
    private fun createPermission(
        resource: String,
        action: String,
        description: String,
        isSystem: Boolean,
        createdAt: kotlinx.datetime.Instant
    ): Permission {
        return Permission(
            id = DomainId.generate(),
            resource = resource,
            action = action,
            description = description,
            isSystem = isSystem,
            createdAt = createdAt
        )
    }
}