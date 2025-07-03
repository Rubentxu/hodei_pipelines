package dev.rubentxu.hodei.security.infrastructure.persistence

import dev.rubentxu.hodei.shared.domain.primitives.DomainId
import dev.rubentxu.hodei.security.domain.repositories.UserRepository
import dev.rubentxu.hodei.security.domain.entities.User
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.util.concurrent.ConcurrentHashMap

class InMemoryUserRepository : UserRepository {
    private val users = ConcurrentHashMap<DomainId, User>()
    private val usernameIndex = ConcurrentHashMap<String, DomainId>()
    private val emailIndex = ConcurrentHashMap<String, DomainId>()
    
    override suspend fun findById(id: DomainId): User? {
        return users[id]
    }
    
    override suspend fun findByUsername(username: String): User? {
        val userId = usernameIndex[username] ?: return null
        return users[userId]
    }
    
    override suspend fun findByEmail(email: String): User? {
        val userId = emailIndex[email] ?: return null
        return users[userId]
    }
    
    override suspend fun save(user: User): User {
        users[user.id] = user
        usernameIndex[user.username] = user.id
        emailIndex[user.email] = user.id
        return user
    }
    
    override suspend fun update(user: User): User {
        val existingUser = users[user.id] ?: throw IllegalArgumentException("User not found: ${user.id}")
        
        // Update indexes if username or email changed
        if (existingUser.username != user.username) {
            usernameIndex.remove(existingUser.username)
            usernameIndex[user.username] = user.id
        }
        
        if (existingUser.email != user.email) {
            emailIndex.remove(existingUser.email)
            emailIndex[user.email] = user.id
        }
        
        users[user.id] = user
        return user
    }
    
    override suspend fun delete(id: DomainId): Boolean {
        val user = users.remove(id) ?: return false
        usernameIndex.remove(user.username)
        emailIndex.remove(user.email)
        return true
    }
    
    override suspend fun findAll(page: Int, size: Int): List<User> {
        val allUsers = users.values.toList()
        val startIndex = page * size
        val endIndex = minOf(startIndex + size, allUsers.size)
        
        return if (startIndex < allUsers.size) {
            allUsers.subList(startIndex, endIndex)
        } else {
            emptyList()
        }
    }
    
    override suspend fun findActiveUsers(): List<User> {
        return users.values.filter { it.isActive }
    }
    
    override suspend fun existsByUsername(username: String): Boolean {
        return usernameIndex.containsKey(username)
    }
    
    override suspend fun existsByEmail(email: String): Boolean {
        return emailIndex.containsKey(email)
    }
    
    override fun streamUsers(): Flow<User> {
        return flowOf(*users.values.toTypedArray())
    }
}