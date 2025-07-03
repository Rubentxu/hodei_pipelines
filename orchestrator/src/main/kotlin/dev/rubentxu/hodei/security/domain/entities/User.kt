package dev.rubentxu.hodei.security.domain.entities

import dev.rubentxu.hodei.shared.domain.primitives.DomainId
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class User(
    val id: DomainId,
    val username: String,
    val email: String,
    val roles: Set<DomainId>,
    val isActive: Boolean = true,
    val createdAt: Instant,
    val updatedAt: Instant,
    val lastLoginAt: Instant? = null
) {
    init {
        require(username.isNotBlank()) { "Username cannot be blank" }
        require(email.isNotBlank()) { "Email cannot be blank" }
        require(email.contains("@")) { "Email must be valid" }
    }
    
    fun assignRole(roleId: DomainId): User =
        copy(roles = roles + roleId, updatedAt = kotlinx.datetime.Clock.System.now())
    
    fun removeRole(roleId: DomainId): User =
        copy(roles = roles - roleId, updatedAt = kotlinx.datetime.Clock.System.now())
    
    fun deactivate(): User =
        copy(isActive = false, updatedAt = kotlinx.datetime.Clock.System.now())
    
    fun activate(): User =
        copy(isActive = true, updatedAt = kotlinx.datetime.Clock.System.now())
    
    fun recordLogin(): User =
        copy(lastLoginAt = kotlinx.datetime.Clock.System.now())
}

@Serializable
data class Role(
    val id: DomainId,
    val name: String,
    val description: String,
    val permissions: Set<DomainId>,
    val isSystem: Boolean = false,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    init {
        require(name.isNotBlank()) { "Role name cannot be blank" }
    }
    
    fun addPermission(permissionId: DomainId): Role =
        copy(permissions = permissions + permissionId, updatedAt = kotlinx.datetime.Clock.System.now())
    
    fun removePermission(permissionId: DomainId): Role =
        copy(permissions = permissions - permissionId, updatedAt = kotlinx.datetime.Clock.System.now())
}

@Serializable
data class Permission(
    val id: DomainId,
    val resource: String,
    val action: String,
    val description: String,
    val isSystem: Boolean = false,
    val createdAt: Instant
) {
    init {
        require(resource.isNotBlank()) { "Resource cannot be blank" }
        require(action.isNotBlank()) { "Action cannot be blank" }
    }
    
    val fullName: String
        get() = "$resource:$action"
}

@Serializable
data class AuditLog(
    val id: DomainId,
    val userId: DomainId,
    val action: String,
    val resource: String,
    val resourceId: DomainId? = null,
    val details: Map<String, String> = emptyMap(),
    val ipAddress: String? = null,
    val userAgent: String? = null,
    val timestamp: Instant
) {
    init {
        require(action.isNotBlank()) { "Action cannot be blank" }
        require(resource.isNotBlank()) { "Resource cannot be blank" }
    }
}