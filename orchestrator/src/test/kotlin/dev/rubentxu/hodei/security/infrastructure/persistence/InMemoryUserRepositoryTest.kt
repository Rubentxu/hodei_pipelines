package dev.rubentxu.hodei.security.infrastructure.persistence

import dev.rubentxu.hodei.shared.domain.primitives.DomainId
import dev.rubentxu.hodei.security.domain.entities.User
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested

@DisplayName("InMemory User Repository Tests")
class InMemoryUserRepositoryTest {
    
    private lateinit var repository: dev.rubentxu.hodei.security.infrastructure.persistence.InMemoryUserRepository
    private lateinit var testUser: User
    
    @BeforeEach
    fun setUp() {
        repository = dev.rubentxu.hodei.security.infrastructure.persistence.InMemoryUserRepository()
        val now = Clock.System.now()
        testUser = User(
            id = DomainId.generate(),
            username = "testuser",
            email = "test@example.com",
            roles = setOf(DomainId.generate()),
            isActive = true,
            createdAt = now,
            updatedAt = now
        )
    }
    
    @Nested
    @DisplayName("Save Operations")
    inner class SaveOperations {
        
        @Test
        @DisplayName("Should save user successfully")
        fun shouldSaveUserSuccessfully() = runTest {
            // When
            val savedUser = repository.save(testUser)
            
            // Then
            assertEquals(testUser, savedUser)
            val retrievedUser = repository.findById(testUser.id)
            assertEquals(testUser, retrievedUser)
        }
        
        @Test
        @DisplayName("Should update indexes when saving user")
        fun shouldUpdateIndexesWhenSavingUser() = runTest {
            // When
            repository.save(testUser)
            
            // Then
            val userByUsername = repository.findByUsername(testUser.username)
            val userByEmail = repository.findByEmail(testUser.email)
            assertEquals(testUser, userByUsername)
            assertEquals(testUser, userByEmail)
        }
        
        @Test
        @DisplayName("Should overwrite existing user when saving with same ID")
        fun shouldOverwriteExistingUserWhenSavingWithSameId() = runTest {
            // Given
            repository.save(testUser)
            val updatedUser = testUser.copy(username = "updateduser")
            
            // When
            repository.save(updatedUser)
            
            // Then
            val retrievedUser = repository.findById(testUser.id)
            assertEquals("updateduser", retrievedUser?.username)
        }
    }
    
    @Nested
    @DisplayName("Update Operations")
    inner class UpdateOperations {
        
        @BeforeEach
        fun setUpUser() = runTest {
            repository.save(testUser)
        }
        
        @Test
        @DisplayName("Should update existing user successfully")
        fun shouldUpdateExistingUserSuccessfully() = runTest {
            // Given
            val updatedUser = testUser.copy(email = "updated@example.com")
            
            // When
            val result = repository.update(updatedUser)
            
            // Then
            assertEquals(updatedUser, result)
            val retrievedUser = repository.findById(testUser.id)
            assertEquals("updated@example.com", retrievedUser?.email)
        }
        
        @Test
        @DisplayName("Should update username index when username changes")
        fun shouldUpdateUsernameIndexWhenUsernameChanges() = runTest {
            // Given
            val newUsername = "newusername"
            val updatedUser = testUser.copy(username = newUsername)
            
            // When
            repository.update(updatedUser)
            
            // Then
            assertNull(repository.findByUsername(testUser.username))
            val userByNewUsername = repository.findByUsername(newUsername)
            assertEquals(updatedUser, userByNewUsername)
        }
        
        @Test
        @DisplayName("Should update email index when email changes")
        fun shouldUpdateEmailIndexWhenEmailChanges() = runTest {
            // Given
            val newEmail = "newemail@example.com"
            val updatedUser = testUser.copy(email = newEmail)
            
            // When
            repository.update(updatedUser)
            
            // Then
            assertNull(repository.findByEmail(testUser.email))
            val userByNewEmail = repository.findByEmail(newEmail)
            assertEquals(updatedUser, userByNewEmail)
        }
        
        @Test
        @DisplayName("Should throw exception when updating non-existent user")
        fun shouldThrowExceptionWhenUpdatingNonExistentUser() = runTest {
            // Given
            val nonExistentUser = testUser.copy(id = DomainId.generate())
            
            // When/Then
            try {
                repository.update(nonExistentUser)
                fail("Expected IllegalArgumentException to be thrown")
            } catch (e: IllegalArgumentException) {
                assertTrue(e.message?.contains("User not found") == true)
            }
        }
    }
    
    @Nested
    @DisplayName("Find Operations")
    inner class FindOperations {
        
        @BeforeEach
        fun setUpUser() = runTest {
            repository.save(testUser)
        }
        
        @Test
        @DisplayName("Should find user by ID")
        fun shouldFindUserById() = runTest {
            // When
            val foundUser = repository.findById(testUser.id)
            
            // Then
            assertEquals(testUser, foundUser)
        }
        
        @Test
        @DisplayName("Should return null when user ID not found")
        fun shouldReturnNullWhenUserIdNotFound() = runTest {
            // When
            val foundUser = repository.findById(DomainId.generate())
            
            // Then
            assertNull(foundUser)
        }
        
        @Test
        @DisplayName("Should find user by username")
        fun shouldFindUserByUsername() = runTest {
            // When
            val foundUser = repository.findByUsername(testUser.username)
            
            // Then
            assertEquals(testUser, foundUser)
        }
        
        @Test
        @DisplayName("Should return null when username not found")
        fun shouldReturnNullWhenUsernameNotFound() = runTest {
            // When
            val foundUser = repository.findByUsername("nonexistent")
            
            // Then
            assertNull(foundUser)
        }
        
        @Test
        @DisplayName("Should find user by email")
        fun shouldFindUserByEmail() = runTest {
            // When
            val foundUser = repository.findByEmail(testUser.email)
            
            // Then
            assertEquals(testUser, foundUser)
        }
        
        @Test
        @DisplayName("Should return null when email not found")
        fun shouldReturnNullWhenEmailNotFound() = runTest {
            // When
            val foundUser = repository.findByEmail("nonexistent@example.com")
            
            // Then
            assertNull(foundUser)
        }
    }
    
    @Nested
    @DisplayName("Delete Operations")
    inner class DeleteOperations {
        
        @BeforeEach
        fun setUpUser() = runTest {
            repository.save(testUser)
        }
        
        @Test
        @DisplayName("Should delete existing user successfully")
        fun shouldDeleteExistingUserSuccessfully() = runTest {
            // When
            val result = repository.delete(testUser.id)
            
            // Then
            assertTrue(result)
            assertNull(repository.findById(testUser.id))
            assertNull(repository.findByUsername(testUser.username))
            assertNull(repository.findByEmail(testUser.email))
        }
        
        @Test
        @DisplayName("Should return false when deleting non-existent user")
        fun shouldReturnFalseWhenDeletingNonExistentUser() = runTest {
            // When
            val result = repository.delete(DomainId.generate())
            
            // Then
            assertFalse(result)
        }
        
        @Test
        @DisplayName("Should clean up indexes when deleting user")
        fun shouldCleanUpIndexesWhenDeletingUser() = runTest {
            // When
            repository.delete(testUser.id)
            
            // Then
            assertNull(repository.findByUsername(testUser.username))
            assertNull(repository.findByEmail(testUser.email))
        }
    }
    
    @Nested
    @DisplayName("List Operations")
    inner class ListOperations {
        
        @Test
        @DisplayName("Should return all users")
        fun shouldReturnAllUsers() = runTest {
            // Given
            val user1 = testUser
            val user2 = testUser.copy(id = DomainId.generate(), username = "user2", email = "user2@example.com")
            repository.save(user1)
            repository.save(user2)
            
            // When
            val allUsers = repository.findAll()
            
            // Then
            assertEquals(2, allUsers.size)
            assertTrue(allUsers.contains(user1))
            assertTrue(allUsers.contains(user2))
        }
        
        @Test
        @DisplayName("Should return paginated users")
        fun shouldReturnPaginatedUsers() = runTest {
            // Given - save 5 users
            repeat(5) { index ->
                val user = testUser.copy(
                    id = DomainId.generate(),
                    username = "user$index",
                    email = "user$index@example.com"
                )
                repository.save(user)
            }
            
            // When
            val firstPage = repository.findAll(page = 0, size = 2)
            val secondPage = repository.findAll(page = 1, size = 2)
            
            // Then
            assertEquals(2, firstPage.size)
            assertEquals(2, secondPage.size)
            // Ensure no overlap
            val firstPageIds = firstPage.map { it.id }.toSet()
            val secondPageIds = secondPage.map { it.id }.toSet()
            assertTrue(firstPageIds.intersect(secondPageIds).isEmpty())
        }
        
        @Test
        @DisplayName("Should return only active users")
        fun shouldReturnOnlyActiveUsers() = runTest {
            // Given
            val activeUser = testUser
            val inactiveUser = testUser.copy(
                id = DomainId.generate(),
                username = "inactive",
                email = "inactive@example.com",
                isActive = false
            )
            repository.save(activeUser)
            repository.save(inactiveUser)
            
            // When
            val activeUsers = repository.findActiveUsers()
            
            // Then
            assertEquals(1, activeUsers.size)
            assertEquals(activeUser, activeUsers.first())
        }
    }
    
    @Nested
    @DisplayName("Existence Checks")
    inner class ExistenceChecks {
        
        @BeforeEach
        fun setUpUser() = runTest {
            repository.save(testUser)
        }
        
        @Test
        @DisplayName("Should check if username exists")
        fun shouldCheckIfUsernameExists() = runTest {
            // When/Then
            assertTrue(repository.existsByUsername(testUser.username))
            assertFalse(repository.existsByUsername("nonexistent"))
        }
        
        @Test
        @DisplayName("Should check if email exists")
        fun shouldCheckIfEmailExists() = runTest {
            // When/Then
            assertTrue(repository.existsByEmail(testUser.email))
            assertFalse(repository.existsByEmail("nonexistent@example.com"))
        }
    }
}