package dev.rubentxu.hodei.domain.pool

import dev.rubentxu.hodei.shared.domain.primitives.DomainId
import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class PoolId(val value: String) {
    companion object {
        fun generate(): PoolId = PoolId(DomainId.generate().value)
        fun default(): PoolId = PoolId("default")
    }
}