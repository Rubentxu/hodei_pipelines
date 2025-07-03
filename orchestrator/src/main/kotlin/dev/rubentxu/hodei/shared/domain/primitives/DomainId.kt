package dev.rubentxu.hodei.shared.domain.primitives

import kotlinx.serialization.Serializable
import java.util.*

@Serializable
@JvmInline
value class DomainId(val value: String) {
    init {
        require(value.isNotBlank()) { "Domain ID cannot be blank" }
    }
    
    companion object {
        fun generate(): DomainId = DomainId(UUID.randomUUID().toString())
        fun fromString(value: String): DomainId = DomainId(value)
    }
}