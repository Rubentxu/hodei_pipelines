package dev.rubentxu.hodei.security.infrastructure.persistence

import dev.rubentxu.hodei.shared.domain.primitives.DomainId
import dev.rubentxu.hodei.security.domain.repositories.RoleRepository
import dev.rubentxu.hodei.security.domain.entities.Role
import kotlinx.datetime.Clock
import java.util.concurrent.ConcurrentHashMap

class InMemoryRoleRepository : RoleRepository {
    private val roles = ConcurrentHashMap<DomainId, Role>()
    private val nameIndex = ConcurrentHashMap<String, DomainId>()
    
    init {
        // Initialize with default roles
        initializeDefaultRoles()
    }
    
    override suspend fun findById(id: DomainId): Role? {
        return roles[id]
    }
    
    override suspend fun findByName(name: String): Role? {
        val roleId = nameIndex[name] ?: return null
        return roles[roleId]
    }
    
    override suspend fun save(role: Role): Role {
        roles[role.id] = role
        nameIndex[role.name] = role.id
        return role
    }
    
    override suspend fun update(role: Role): Role {
        val existingRole = roles[role.id] ?: throw IllegalArgumentException("Role not found: ${role.id}")
        
        // Update name index if name changed
        if (existingRole.name != role.name) {
            nameIndex.remove(existingRole.name)
            nameIndex[role.name] = role.id
        }
        
        roles[role.id] = role
        return role
    }
    
    override suspend fun delete(id: DomainId): Boolean {
        val role = roles.remove(id) ?: return false
        nameIndex.remove(role.name)
        return true
    }
    
    override suspend fun findAll(): List<Role> {
        return roles.values.toList()
    }
    
    override suspend fun findSystemRoles(): List<Role> {
        return roles.values.filter { it.isSystem }
    }
    
    override suspend fun existsByName(name: String): Boolean {
        return nameIndex.containsKey(name)
    }
    
    private fun initializeDefaultRoles() {
        val now = Clock.System.now()
        
        val adminRole = Role(
            id = DomainId.generate(),
            name = "ADMIN",
            description = "Administrator role with full system access",
            permissions = emptySet(),
            isSystem = true,
            createdAt = now,
            updatedAt = now
        )
        
        val userRole = Role(
            id = DomainId.generate(),
            name = "USER",
            description = "Standard user role with basic access",
            permissions = emptySet(),
            isSystem = true,
            createdAt = now,
            updatedAt = now
        )
        
        val moderatorRole = Role(
            id = DomainId.generate(),
            name = "MODERATOR",
            description = "Moderator role with elevated permissions",
            permissions = emptySet(),
            isSystem = true,
            createdAt = now,
            updatedAt = now
        )
        
        roles[adminRole.id] = adminRole
        roles[userRole.id] = userRole
        roles[moderatorRole.id] = moderatorRole
        
        nameIndex[adminRole.name] = adminRole.id
        nameIndex[userRole.name] = userRole.id
        nameIndex[moderatorRole.name] = moderatorRole.id
    }
}