package dev.rubentxu.hodei.templatemanagement.domain.repositories

import arrow.core.Either
import dev.rubentxu.hodei.shared.domain.errors.*
import dev.rubentxu.hodei.shared.domain.primitives.*
import dev.rubentxu.hodei.templatemanagement.domain.entities.Template
import dev.rubentxu.hodei.templatemanagement.domain.entities.TemplateStatus
import kotlinx.coroutines.flow.Flow

interface TemplateRepository {
    suspend fun findById(id: DomainId): Either<DomainError, Template?>
    suspend fun findByName(name: String): Either<DomainError, List<Template>>
    suspend fun findByNameAndVersion(name: String, version: String): Either<DomainError, Template?>
    suspend fun findByStatus(status: TemplateStatus): Flow<Template>
    suspend fun search(query: String, status: TemplateStatus? = null): Flow<Template>
    suspend fun save(template: Template): Either<DomainError, Template>
    suspend fun update(template: Template): Either<DomainError, Template>
    suspend fun delete(id: DomainId): Either<DomainError, Unit>
    suspend fun exists(id: DomainId): Either<DomainError, Boolean>
    suspend fun existsByNameAndVersion(name: String, version: String): Either<DomainError, Boolean>
    suspend fun list(page: Int, pageSize: Int): Either<DomainError, Pair<List<Template>, Long>>
}