package dev.rubentxu.hodei.security.domain.repositories

import dev.rubentxu.hodei.shared.domain.primitives.DomainId
import dev.rubentxu.hodei.security.domain.entities.User
import dev.rubentxu.hodei.security.domain.entities.Role
import dev.rubentxu.hodei.security.domain.entities.Permission
import kotlinx.coroutines.flow.Flow

interface UserRepository {
    suspend fun findById(id: DomainId): User?
    suspend fun findByUsername(username: String): User?
    suspend fun findByEmail(email: String): User?
    suspend fun save(user: User): User
    suspend fun update(user: User): User
    suspend fun delete(id: DomainId): Boolean
    suspend fun findAll(page: Int = 0, size: Int = 20): List<User>
    suspend fun findActiveUsers(): List<User>
    suspend fun existsByUsername(username: String): Boolean
    suspend fun existsByEmail(email: String): Boolean
    fun streamUsers(): Flow<User>
}

interface RoleRepository {
    suspend fun findById(id: DomainId): Role?
    suspend fun findByName(name: String): Role?
    suspend fun save(role: Role): Role
    suspend fun update(role: Role): Role
    suspend fun delete(id: DomainId): Boolean
    suspend fun findAll(): List<Role>
    suspend fun findSystemRoles(): List<Role>
    suspend fun existsByName(name: String): Boolean
}

interface PermissionRepository {
    suspend fun findById(id: DomainId): Permission?
    suspend fun findByResourceAndAction(resource: String, action: String): Permission?
    suspend fun save(permission: Permission): Permission
    suspend fun update(permission: Permission): Permission
    suspend fun delete(id: DomainId): Boolean
    suspend fun findAll(): List<Permission>
    suspend fun findByResource(resource: String): List<Permission>
    suspend fun findSystemPermissions(): List<Permission>
}